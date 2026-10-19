package com.anirudh.storage.minis3.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Client for the miniS3 v2 object API and pre-signed URLs, authenticated with an API key.
 * <pre>{@code
 * try (MiniS3Client storage = MiniS3Client.builder()
 *         .endpoint("https://laptop.tailnet.ts.net/storage/minis3")
 *         .apiKey(System.getenv("MINIS3_API_KEY"))
 *         .build()) {
 *     storage.putObject("invoices", "2026/42.pdf", Path.of("42.pdf"), "application/pdf");
 * }
 * }</pre>
 * Thread-safe; create one and share it. Every failure is a {@link MiniS3Exception}.
 *
 * @author Anirudh
 * @since 19/10/26
 */
public final class MiniS3Client implements AutoCloseable {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int DEFAULT_PAGE_SIZE = 1000;

    private final String endpoint;
    private final String apiKey;
    private final HttpClient httpClient;
    private final boolean ownsHttpClient;
    private final Duration requestTimeout;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private MiniS3Client(Builder builder) {
        String base = Objects.requireNonNull(builder.endpoint, "endpoint is required").toString();
        this.endpoint = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.apiKey = requireText(builder.apiKey, "apiKey");
        this.ownsHttpClient = builder.httpClient == null;
        this.httpClient = ownsHttpClient
                ? HttpClient.newBuilder().connectTimeout(builder.connectTimeout).build()
                : builder.httpClient;
        this.requestTimeout = builder.requestTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---------- upload ----------

    /**
     * Creates or replaces the object, streaming the content (it is never fully buffered).
     * The stream is read to the end but not closed.
     */
    public ObjectInfo putObject(String bucket, String key, InputStream content, String contentType) {
        Objects.requireNonNull(content, "content");
        return put(bucket, key, HttpRequest.BodyPublishers.ofInputStream(() -> content), contentType);
    }

    public ObjectInfo putObject(String bucket, String key, byte[] content, String contentType) {
        Objects.requireNonNull(content, "content");
        return put(bucket, key, HttpRequest.BodyPublishers.ofByteArray(content), contentType);
    }

    public ObjectInfo putObject(String bucket, String key, Path file, String contentType) {
        try {
            return put(bucket, key, HttpRequest.BodyPublishers.ofFile(file), contentType);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ObjectInfo put(String bucket, String key, HttpRequest.BodyPublisher body, String contentType) {
        HttpRequest request = authorized(objectUri(bucket, key))
                .header("Content-Type", contentType == null ? DEFAULT_CONTENT_TYPE : contentType)
                .PUT(body)
                .build();
        return readJson(sendForBytes(request), ObjectInfo.class);
    }

    // ---------- download ----------

    /**
     * Streams the object. Close the result to release the connection.
     *
     * @throws MiniS3NotFoundException if the bucket or object doesn't exist
     */
    public MiniS3Object getObject(String bucket, String key) {
        HttpRequest request = authorized(objectUri(bucket, key)).GET().build();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (!isSuccess(response.statusCode())) {
            try (InputStream errorBody = response.body()) {
                throw error(response.statusCode(), errorBody.readAllBytes());
            } catch (IOException e) {
                throw error(response.statusCode(), new byte[0]);
            }
        }
        return new MiniS3Object(infoFromHeaders(key, response.headers()), response.body());
    }

    public byte[] getObjectBytes(String bucket, String key) {
        return getObject(bucket, key).readAllBytes();
    }

    /**
     * @return the object's metadata, or empty if it doesn't exist
     */
    public Optional<ObjectInfo> headObject(String bucket, String key) {
        HttpRequest request = authorized(objectUri(bucket, key))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (!isSuccess(response.statusCode())) {
            throw error(response.statusCode(), new byte[0]);
        }
        return Optional.of(infoFromHeaders(key, response.headers()));
    }

    public boolean objectExists(String bucket, String key) {
        return headObject(bucket, key).isPresent();
    }

    // ---------- delete ----------

    /**
     * @throws MiniS3NotFoundException if the object doesn't exist
     */
    public void deleteObject(String bucket, String key) {
        sendForBytes(authorized(objectUri(bucket, key)).DELETE().build());
    }

    // ---------- list ----------

    /**
     * One page of keys under {@code prefix}, in key order.
     *
     * @param prefix optional, null or "" for everything
     * @param cursor null for the first page, then {@link ObjectPage#nextCursor()}
     * @param limit  page size, 1-1000
     */
    public ObjectPage listObjects(String bucket, String prefix, String cursor, int limit) {
        StringBuilder uri = new StringBuilder(bucketUri(bucket)).append("/objects?limit=").append(limit);
        if (prefix != null && !prefix.isEmpty()) {
            uri.append("&prefix=").append(PathEncoder.encode(prefix));
        }
        if (cursor != null && !cursor.isEmpty()) {
            uri.append("&cursor=").append(PathEncoder.encode(cursor));
        }
        HttpRequest request = authorized(URI.create(uri.toString())).GET().build();
        return readJson(sendForBytes(request), ObjectPage.class);
    }

    /**
     * Every object under {@code prefix}, fetching further pages lazily as the stream is consumed.
     */
    public Stream<ObjectInfo> listAllObjects(String bucket, String prefix) {
        return listAllObjects(bucket, prefix, DEFAULT_PAGE_SIZE);
    }

    public Stream<ObjectInfo> listAllObjects(String bucket, String prefix, int pageSize) {
        Iterator<ObjectInfo> iterator = new Iterator<>() {
            private Iterator<ObjectInfo> current = null;
            private String cursor = null;
            private boolean lastPage = false;

            @Override
            public boolean hasNext() {
                while ((current == null || !current.hasNext()) && !lastPage) {
                    ObjectPage page = listObjects(bucket, prefix, cursor, pageSize);
                    current = page.objects().iterator();
                    cursor = page.nextCursor();
                    lastPage = !page.truncated();
                }
                return current != null && current.hasNext();
            }

            @Override
            public ObjectInfo next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    // ---------- pre-signed URLs ----------

    /**
     * A link anyone can use to download the object until it expires (at most 7 days). Needs the READ scope.
     */
    public PresignedUrl presignGet(String bucket, String key, Duration expiresIn) {
        return presign(bucket, key, "GET", expiresIn);
    }

    /**
     * A link anyone can use to upload (PUT) the object until it expires (at most 7 days). Needs the WRITE scope.
     */
    public PresignedUrl presignPut(String bucket, String key, Duration expiresIn) {
        return presign(bucket, key, "PUT", expiresIn);
    }

    private PresignedUrl presign(String bucket, String key, String method, Duration expiresIn) {
        requireText(key, "key");
        Objects.requireNonNull(expiresIn, "expiresIn");
        byte[] body = json.writeValueAsBytes(Map.of(
                "key", key,
                "method", method,
                "expiresInSeconds", expiresIn.toSeconds()));
        HttpRequest request = authorized(URI.create(bucketUri(bucket) + "/presign"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return readJson(sendForBytes(request), PresignedUrl.class);
    }

    @Override
    public void close() {
        if (ownsHttpClient) {
            httpClient.close();
        }
    }

    // ---------- plumbing ----------

    private String bucketUri(String bucket) {
        return endpoint + "/api/v2/buckets/" + PathEncoder.encode(requireText(bucket, "bucket"));
    }

    private URI objectUri(String bucket, String key) {
        return URI.create(bucketUri(bucket) + "/objects/" + PathEncoder.encodeKey(requireText(key, "key")));
    }

    private HttpRequest.Builder authorized(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "minis3-client");
        if (requestTimeout != null) {
            builder.timeout(requestTimeout);
        }
        return builder;
    }

    private HttpResponse<byte[]> sendForBytes(HttpRequest request) {
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (!isSuccess(response.statusCode())) {
            throw error(response.statusCode(), response.body());
        }
        return response;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClient.send(request, handler);
        } catch (IOException e) {
            throw new MiniS3Exception("Request to miniS3 failed: " + request.method() + " " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiniS3Exception("Interrupted while calling miniS3", e);
        }
    }

    private <T> T readJson(HttpResponse<byte[]> response, Class<T> type) {
        try {
            return json.readValue(response.body(), type);
        } catch (JacksonException e) {
            throw new MiniS3Exception("Unexpected response from miniS3: " + e.getOriginalMessage(), e);
        }
    }

    private MiniS3Exception error(int status, byte[] body) {
        String message = errorMessage(body).orElse("HTTP " + status);
        return switch (status) {
            case 404 -> new MiniS3NotFoundException(message);
            case 401, 403 -> new MiniS3AccessDeniedException(status, message);
            default -> new MiniS3Exception(status, message);
        };
    }

    // the service answers errors with {"message": "...", "timestamp": "..."}
    private Optional<String> errorMessage(byte[] body) {
        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        try {
            JsonNode message = json.readTree(body).get("message");
            if (message != null && message.isString()) {
                return Optional.of(message.asString());
            }
        } catch (JacksonException ignored) {
            // not JSON, fall back to the raw text
        }
        return Optional.of(new String(body, StandardCharsets.UTF_8));
    }

    private static ObjectInfo infoFromHeaders(String key, HttpHeaders headers) {
        long size = headers.firstValueAsLong("Content-Length").orElse(-1);
        String contentType = headers.firstValue("Content-Type").orElse(null);
        String etag = headers.firstValue("ETag").map(MiniS3Client::unquote).orElse(null);
        Instant lastModified = headers.firstValue("Last-Modified").map(MiniS3Client::parseHttpDate).orElse(null);
        return new ObjectInfo(key, size, contentType, etag, lastModified);
    }

    private static String unquote(String etag) {
        String value = etag.startsWith("W/") ? etag.substring(2) : etag;
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static Instant parseHttpDate(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static final class Builder {

        private URI endpoint;
        private String apiKey;
        private HttpClient httpClient;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout;

        private Builder() {
        }

        /**
         * Base URL including the context path, e.g. {@code http://localhost:2399/storage/minis3}.
         */
        public Builder endpoint(String endpoint) {
            return endpoint(URI.create(endpoint));
        }

        public Builder endpoint(URI endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * An API key ({@code ms3_...}) created through {@code POST /api/v1/keys}.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Use your own HttpClient (proxy, TLS, executor). It is not closed by {@link MiniS3Client#close()}.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Ignored when a custom {@link #httpClient(HttpClient)} is set. Default 10 seconds.
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout);
            return this;
        }

        /**
         * Time to wait for response headers. Default: no limit, since a large upload must finish before the
         * server answers.
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public MiniS3Client build() {
            return new MiniS3Client(this);
        }
    }
}

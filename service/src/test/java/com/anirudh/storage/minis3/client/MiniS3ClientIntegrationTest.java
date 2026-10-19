package com.anirudh.storage.minis3.client;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The SDK against the real service on a random port: real HTTP, real security chain, H2 and a temp storage dir.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:clienttest",
        "minis3.upload.max-size=1KB"
})
@ActiveProfiles("test")
class MiniS3ClientIntegrationTest {

    // md5("hello world")
    private static final String HELLO_MD5 = "5eb63bbbe01eeed093cb22bb8f5acdc3";
    private static final Path STORAGE_DIR = createStorageDir();

    @DynamicPropertySource
    static void storageLocation(DynamicPropertyRegistry registry) {
        registry.add("minis3.app.storage.location", STORAGE_DIR::toString);
    }

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private String endpoint;
    private String token;
    private String bucket;
    private MiniS3Client client;

    @BeforeEach
    void setup() throws Exception {
        endpoint = "http://localhost:" + port + "/storage/minis3";
        token = registerAndLogin();
        bucket = "bucket-" + UUID.randomUUID().toString().substring(0, 8);
        post("/api/v1/bucket/", "{\"bucketName\":\"" + bucket + "\"}", 201);
        client = clientWithKey("{\"name\":\"sdk-test\",\"scopes\":[\"READ\",\"WRITE\",\"DELETE\"]}");
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void putGetHeadDeleteRoundTrip() {
        ObjectInfo put = client.putObject(bucket, "docs/hello.txt", "hello world".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertEquals("docs/hello.txt", put.key());
        assertEquals(11, put.size());
        assertEquals(HELLO_MD5, put.etag());
        assertNotNull(put.lastModified());

        try (MiniS3Object object = client.getObject(bucket, "docs/hello.txt")) {
            assertEquals(HELLO_MD5, object.info().etag());
            assertTrue(object.info().contentType().startsWith("text/plain"));
            assertEquals("hello world", new String(object.readAllBytes(), StandardCharsets.UTF_8));
        }

        ObjectInfo head = client.headObject(bucket, "docs/hello.txt").orElseThrow();
        assertEquals(11, head.size());
        assertEquals(HELLO_MD5, head.etag());
        // header (seconds precision) and JSON agree on the moment
        assertTrue(Duration.between(head.lastModified(), put.lastModified()).abs().getSeconds() <= 1);

        client.deleteObject(bucket, "docs/hello.txt");
        assertTrue(client.headObject(bucket, "docs/hello.txt").isEmpty());
        assertFalse(client.objectExists(bucket, "docs/hello.txt"));
        assertThrows(MiniS3NotFoundException.class, () -> client.getObject(bucket, "docs/hello.txt"));
        assertThrows(MiniS3NotFoundException.class, () -> client.deleteObject(bucket, "docs/hello.txt"));
    }

    @Test
    void streamsAndFilesAndAwkwardKeys() throws IOException {
        String key = "reports/Q1 2026/résumé + notes.txt";
        client.putObject(bucket, key, new ByteArrayInputStream("streamed".getBytes(StandardCharsets.UTF_8)), "text/plain");
        assertEquals("streamed", new String(client.getObjectBytes(bucket, key), StandardCharsets.UTF_8));

        Path file = Files.writeString(Files.createTempFile("minis3", ".txt"), "from a file");
        client.putObject(bucket, "files/upload.txt", file, null);
        ObjectInfo info = client.headObject(bucket, "files/upload.txt").orElseThrow();
        assertEquals("application/octet-stream", info.contentType());
        assertEquals("from a file", new String(client.getObjectBytes(bucket, "files/upload.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void listAllObjectsPagesLazily() {
        for (String key : List.of("a/1", "a/2", "a/3", "a/4", "a/5", "b/1")) {
            client.putObject(bucket, key, new byte[]{1}, null);
        }

        ObjectPage first = client.listObjects(bucket, "a/", null, 2);
        assertEquals(List.of("a/1", "a/2"), first.objects().stream().map(ObjectInfo::key).toList());
        assertTrue(first.truncated());

        List<String> all = client.listAllObjects(bucket, "a/", 2).map(ObjectInfo::key).toList();
        assertEquals(List.of("a/1", "a/2", "a/3", "a/4", "a/5"), all);
        assertEquals(6, client.listAllObjects(bucket, null).count());
    }

    @Test
    void presignedUrlsWorkWithoutCredentials() throws Exception {
        client.putObject(bucket, "share/photo.txt", "hello world".getBytes(StandardCharsets.UTF_8), "text/plain");

        PresignedUrl download = client.presignGet(bucket, "share/photo.txt", Duration.ofMinutes(5));
        assertEquals("GET", download.method());
        assertTrue(download.expiresAt().isAfter(Instant.now()));
        HttpResponse<String> got = http.send(HttpRequest.newBuilder(download.url()).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, got.statusCode());
        assertEquals("hello world", got.body());

        PresignedUrl upload = client.presignPut(bucket, "share/uploaded.txt", Duration.ofMinutes(5));
        HttpResponse<String> put = http.send(HttpRequest.newBuilder(upload.url())
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofString("via link")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, put.statusCode());
        assertEquals("via link", new String(client.getObjectBytes(bucket, "share/uploaded.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void errorsAreTyped() throws Exception {
        try (MiniS3Client readOnly = clientWithKey("{\"name\":\"read-only\",\"scopes\":[\"READ\"]}")) {
            MiniS3AccessDeniedException denied = assertThrows(MiniS3AccessDeniedException.class,
                    () -> readOnly.putObject(bucket, "x.txt", new byte[]{1}, null));
            assertEquals(403, denied.statusCode());
        }

        try (MiniS3Client badKey = MiniS3Client.builder().endpoint(endpoint).apiKey("ms3_000000000000_nope").build()) {
            assertEquals(401, assertThrows(MiniS3AccessDeniedException.class, () -> badKey.listObjects(bucket, null, null, 10)).statusCode());
        }

        assertThrows(MiniS3NotFoundException.class, () -> client.putObject("no-such-bucket", "x", new byte[]{1}, null));

        MiniS3Exception tooLarge = assertThrows(MiniS3Exception.class, () -> client.putObject(bucket, "big.bin", new byte[2048], null));
        assertEquals(413, tooLarge.statusCode());

        try (MiniS3Client unreachable = MiniS3Client.builder().endpoint("http://localhost:1").apiKey("ms3_x").build()) {
            assertEquals(0, assertThrows(MiniS3Exception.class, () -> unreachable.headObject(bucket, "x")).statusCode());
        }
    }

    private MiniS3Client clientWithKey(String keyRequest) throws Exception {
        String apiKey = JsonPath.read(post("/api/v1/keys", keyRequest, 201), "$.apiKey");
        return MiniS3Client.builder().endpoint(endpoint).apiKey(apiKey).build();
    }

    private String registerAndLogin() throws Exception {
        String email = "sdk-" + UUID.randomUUID() + "@example.com";
        String credentials = "\"email\":\"" + email + "\",\"password\":\"password123\"";
        post("/api/v1/users/register", "{\"name\":\"sdk tester\"," + credentials + "}", 201);
        return JsonPath.read(post("/api/v1/auth/login", "{" + credentials + "}", 200), "$.token");
    }

    private String post(String path, String json, int expectedStatus) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), () -> path + " -> " + response.body());
        return response.body();
    }

    private static Path createStorageDir() {
        try {
            return Files.createTempDirectory("minis3-client-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

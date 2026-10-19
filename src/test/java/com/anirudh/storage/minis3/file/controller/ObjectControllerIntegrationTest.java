package com.anirudh.storage.minis3.file.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2 object API end to end: real security chain, H2 and a temporary storage directory.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:objecttest",
        "jwt.secret=c2VjcmV0LWtleS1mb3ItbWluaXMzLWludGVncmF0aW9uLXRlc3RzIQ==",
        "jwt.expiration=10m",
        "minis3.upload.max-size=1KB"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObjectControllerIntegrationTest {

    // md5("hello world")
    private static final String HELLO_ETAG = "\"5eb63bbbe01eeed093cb22bb8f5acdc3\"";

    private static final Path STORAGE_DIR = createStorageDir();

    @DynamicPropertySource
    static void storageLocation(DynamicPropertyRegistry registry) {
        registry.add("minis3.app.storage.location", STORAGE_DIR::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    private String token;
    private String bucketName;
    private String bucketId;

    @BeforeEach
    void setup() throws Exception {
        token = registerAndLogin();
        bucketName = "bucket-" + UUID.randomUUID().toString().substring(0, 8);
        bucketId = createBucket(token, bucketName);
    }

    @Test
    void putGetHeadRoundTrip() throws Exception {
        mockMvc.perform(withAuth(put(objectUrl("docs/readme.txt")), token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello world".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, HELLO_ETAG))
                .andExpect(jsonPath("$.key").value("docs/readme.txt"))
                .andExpect(jsonPath("$.size").value(11))
                // MockMvc appends the request charset to Content-Type; a real Tomcat request doesn't
                .andExpect(jsonPath("$.contentType").value(startsWith("text/plain")));

        mockMvc.perform(withAuth(get(objectUrl("docs/readme.txt")), token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, HELLO_ETAG))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/plain")))
                .andExpect(content().string("hello world"));

        mockMvc.perform(withAuth(head(objectUrl("docs/readme.txt")), token))
                .andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 11))
                .andExpect(header().string(HttpHeaders.ETAG, HELLO_ETAG))
                .andExpect(content().string(""));
    }

    @Test
    void conditionalAndRangeRequests() throws Exception {
        putText("file.txt", "hello world");

        mockMvc.perform(withAuth(get(objectUrl("file.txt")), token)
                        .header(HttpHeaders.IF_NONE_MATCH, HELLO_ETAG))
                .andExpect(status().isNotModified());

        mockMvc.perform(withAuth(get(objectUrl("file.txt")), token)
                        .header(HttpHeaders.RANGE, "bytes=0-4"))
                .andExpect(status().isPartialContent())
                .andExpect(content().string("hello"));
    }

    @Test
    void putOverwritesAndCleansUpOldBytes() throws Exception {
        putText("notes.txt", "first version");

        mockMvc.perform(withAuth(put(objectUrl("notes.txt")), token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("second"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(6));

        mockMvc.perform(withAuth(get(objectUrl("notes.txt")), token))
                .andExpect(content().string("second"));

        // only the current version is left on disk, no temp files or old bytes
        assertEquals(1, filesOnDisk().size());
    }

    @Test
    void listByPrefixWithCursor() throws Exception {
        for (String key : List.of("a/1.txt", "a/2.txt", "a/3.txt", "b/1.txt")) {
            putText(key, "x");
        }

        String firstPage = mockMvc.perform(withAuth(get(listUrl()), token)
                        .param("prefix", "a/")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects.length()").value(2))
                .andExpect(jsonPath("$.objects[0].key").value("a/1.txt"))
                .andExpect(jsonPath("$.objects[1].key").value("a/2.txt"))
                .andExpect(jsonPath("$.truncated").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstPage, "$.nextCursor");

        mockMvc.perform(withAuth(get(listUrl()), token)
                        .param("prefix", "a/")
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects.length()").value(1))
                .andExpect(jsonPath("$.objects[0].key").value("a/3.txt"))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mockMvc.perform(withAuth(get(listUrl()), token).param("cursor", "%%%"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesMetadataAndBytes() throws Exception {
        putText("gone.txt", "bye");

        mockMvc.perform(withAuth(delete(objectUrl("gone.txt")), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(withAuth(get(objectUrl("gone.txt")), token))
                .andExpect(status().isNotFound());
        mockMvc.perform(withAuth(delete(objectUrl("gone.txt")), token))
                .andExpect(status().isNotFound());
        assertEquals(0, filesOnDisk().size());
    }

    @Test
    void scopesAndBucketRestrictionsApply() throws Exception {
        putText("shared.txt", "data");
        String readKey = createKey("{\"name\":\"reader\",\"scopes\":[\"READ\"]}");

        mockMvc.perform(withAuth(get(objectUrl("shared.txt")), readKey))
                .andExpect(status().isOk());
        mockMvc.perform(withAuth(put(objectUrl("other.txt")), readKey).content("x"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withAuth(delete(objectUrl("shared.txt")), readKey))
                .andExpect(status().isForbidden());

        String otherBucketId = createBucket(token, "other-" + UUID.randomUUID().toString().substring(0, 8));
        String restrictedKey = createKey("{\"name\":\"restricted\",\"scopes\":[\"READ\"],\"bucketIds\":[\"" + otherBucketId + "\"]}");
        mockMvc.perform(withAuth(get(objectUrl("shared.txt")), restrictedKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void bucketNamesResolveWithinCallersOwnBuckets() throws Exception {
        putText("private.txt", "secret");
        String otherToken = registerAndLogin();

        mockMvc.perform(withAuth(get(objectUrl("private.txt")), otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(withAuth(get("/api/v2/buckets/{bucket}/objects/x", "no-such-bucket"), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsTooLargeAndFormBodies() throws Exception {
        mockMvc.perform(withAuth(put(objectUrl("big.bin")), token)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[2048]))
                .andExpect(status().isContentTooLarge());
        assertEquals(0, filesOnDisk().size());

        mockMvc.perform(withAuth(put(objectUrl("form.txt")), token)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("a=b"))
                .andExpect(status().isBadRequest());
    }

    private String objectUrl(String key) {
        return "/api/v2/buckets/" + bucketName + "/objects/" + key;
    }

    private String listUrl() {
        return "/api/v2/buckets/" + bucketName + "/objects";
    }

    private void putText(String key, String body) throws Exception {
        mockMvc.perform(withAuth(put(objectUrl(key)), token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated());
    }

    private List<Path> filesOnDisk() throws IOException {
        Path bucketDir = STORAGE_DIR.resolve(bucketId);
        if (!Files.exists(bucketDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(bucketDir)) {
            return files.toList();
        }
    }

    private String registerAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private String createBucket(String authToken, String name) throws Exception {
        String response = mockMvc.perform(withAuth(post("/api/v1/bucket/"), authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucketName\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.bucketPublicId");
    }

    private String createKey(String body) throws Exception {
        String response = mockMvc.perform(withAuth(post("/api/v1/keys"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.apiKey");
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String authToken) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
    }

    private static Path createStorageDir() {
        try {
            return Files.createTempDirectory("minis3-object-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package com.anirudh.storage.minis3.auth.apikey;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the real security chain: JWT and API key filters, URL rules and @PreAuthorize.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apikeytest",
        "jwt.secret=c2VjcmV0LWtleS1mb3ItbWluaXMzLWludGVncmF0aW9uLXRlc3RzIQ==",
        "jwt.expiration=10m"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeySecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String userToken;
    private String alphaBucketId;
    private String betaBucketId;

    @BeforeEach
    void setup() throws Exception {
        userToken = registerAndLogin();
        alphaBucketId = createBucket(userToken, "alpha");
        betaBucketId = createBucket(userToken, "beta");
    }

    @Test
    void readOnlyKeyRestrictedToOneBucket() throws Exception {
        String key = createKey("{\"name\":\"reader\",\"scopes\":[\"READ\"],\"bucketIds\":[\"" + alphaBucketId + "\"]}");

        mockMvc.perform(withAuth(get("/api/v1/bucket/"), key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].bucketId").value(alphaBucketId));

        mockMvc.perform(withAuth(get("/api/v1/file/buckets/{id}/files", alphaBucketId), key))
                .andExpect(status().isOk());

        // bucket outside the key's restriction
        mockMvc.perform(withAuth(get("/api/v1/file/buckets/{id}/files", betaBucketId), key))
                .andExpect(status().isForbidden());

        // missing WRITE / DELETE scopes
        mockMvc.perform(withAuth(post("/api/v1/bucket/"), key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucketName\":\"gamma\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withAuth(delete("/api/v1/bucket/{id}", alphaBucketId), key))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiKeyCannotManageKeys() throws Exception {
        String key = createKey("{\"name\":\"full\",\"scopes\":[\"READ\",\"WRITE\",\"DELETE\"]}");

        mockMvc.perform(withAuth(get("/api/v1/keys"), key))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedKeyIsRejected() throws Exception {
        String createResponse = mockMvc.perform(withAuth(post("/api/v1/keys"), userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"temp\",\"scopes\":[\"READ\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(createResponse, "$.apiKey");
        String keyId = JsonPath.read(createResponse, "$.keyId");

        mockMvc.perform(withAuth(get("/api/v1/bucket/"), key)).andExpect(status().isOk());

        mockMvc.perform(withAuth(delete("/api/v1/keys/{id}", keyId), userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(withAuth(get("/api/v1/bucket/"), key)).andExpect(status().isUnauthorized());

        // listing never exposes the raw key
        mockMvc.perform(withAuth(get("/api/v1/keys"), userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].apiKey").doesNotExist());
    }

    @Test
    void tamperedKeyIsRejected() throws Exception {
        String key = createKey("{\"name\":\"reader\",\"scopes\":[\"READ\"]}");

        mockMvc.perform(withAuth(get("/api/v1/bucket/"), key + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherUserCannotDeleteBucket() throws Exception {
        String otherToken = registerAndLogin();

        mockMvc.perform(withAuth(delete("/api/v1/bucket/{id}", alphaBucketId), otherToken))
                .andExpect(status().isForbidden());

        // and cannot mint a key scoped to someone else's bucket
        mockMvc.perform(withAuth(post("/api/v1/keys"), otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sneaky\",\"scopes\":[\"READ\"],\"bucketIds\":[\"" + alphaBucketId + "\"]}"))
                .andExpect(status().isForbidden());
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

    private String createBucket(String token, String name) throws Exception {
        String response = mockMvc.perform(withAuth(post("/api/v1/bucket/"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucketName\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.bucketPublicId");
    }

    private String createKey(String body) throws Exception {
        String response = mockMvc.perform(withAuth(post("/api/v1/keys"), userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.apiKey");
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}

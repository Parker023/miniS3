package com.anirudh.storage.minis3.hardening;

import com.jayway.jsonpath.JsonPath;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invite code, login lockout and storage quota, with the limits set small.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:limitstest",
        "minis3.limits.invite-code=let-me-in",
        "minis3.limits.login-max-failures=3",
        "minis3.limits.user-quota=100B"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicDeploymentLimitsIntegrationTest {

    private static final Path STORAGE_DIR = createStorageDir();

    @DynamicPropertySource
    static void storageLocation(DynamicPropertyRegistry registry) {
        registry.add("minis3.app.storage.location", STORAGE_DIR::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationNeedsTheInviteCode() throws Exception {
        String email = uniqueEmail();
        register(email, null).andExpect(status().isForbidden());
        register(email, "wrong").andExpect(status().isForbidden());
        register(email, "let-me-in").andExpect(status().isCreated());
    }

    @Test
    void loginLocksOutAfterRepeatedFailures() throws Exception {
        String email = uniqueEmail();
        register(email, "let-me-in").andExpect(status().isCreated());

        for (int i = 0; i < 3; i++) {
            login(email, "wrong-password").andExpect(status().isUnauthorized());
        }
        // even the right password is refused while locked out
        login(email, "password123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void uploadsStopAtTheQuota() throws Exception {
        String email = uniqueEmail();
        register(email, "let-me-in").andExpect(status().isCreated());
        String token = JsonPath.read(login(email, "password123").andReturn().getResponse().getContentAsString(), "$.token");
        mockMvc.perform(post("/api/v1/bucket/").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bucketName\":\"quota\"}"))
                .andExpect(status().isCreated());

        putObject(token, "a.bin", 60).andExpect(status().isCreated());
        // 60 + 60 > 100
        putObject(token, "b.bin", 60).andExpect(status().isInsufficientStorage());
        // replacing a.bin with something smaller frees room
        putObject(token, "a.bin", 30).andExpect(status().isOk());
        putObject(token, "b.bin", 60).andExpect(status().isCreated());
        putObject(token, "c.bin", 20).andExpect(status().isInsufficientStorage());
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String inviteCode) throws Exception {
        String invite = inviteCode == null ? "" : ",\"inviteCode\":\"" + inviteCode + "\"";
        return mockMvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"limits test\",\"email\":\"" + email + "\",\"password\":\"password123\"" + invite + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions putObject(String token, String key, int size) throws Exception {
        return mockMvc.perform(put("/api/v2/buckets/quota/objects/" + key)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(new byte[size]));
    }

    private static String uniqueEmail() {
        return "limits-" + UUID.randomUUID() + "@example.com";
    }

    private static Path createStorageDir() {
        try {
            return Files.createTempDirectory("minis3-limits-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

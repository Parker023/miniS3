package com.anirudh.storage.minis3.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Status codes as a real client sees them. MockMvc doesn't perform the servlet container's error forward
 * (sendError -> /error), so a 403 that turned into 401 on a real server only shows up over real HTTP.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:securityhttptest")
@ActiveProfiles("test")
class SecurityOverHttpIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private String token;
    private String apiKey;

    @BeforeEach
    void setup() throws Exception {
        String email = "http-" + UUID.randomUUID() + "@example.com";
        String credentials = "\"email\":\"" + email + "\",\"password\":\"password123\"";
        send("POST", "/api/v1/users/register", null, "{\"name\":\"http tester\"," + credentials + "}");
        token = JsonPath.read(send("POST", "/api/v1/auth/login", null, "{" + credentials + "}").body(), "$.token");
        apiKey = JsonPath.read(send("POST", "/api/v1/keys", token, "{\"name\":\"http-key\",\"scopes\":[\"READ\"]}").body(), "$.apiKey");
    }

    @Test
    void apiKeyOnKeyManagementIsForbiddenNotUnauthorized() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/keys", apiKey, null);

        assertEquals(403, response.statusCode());
        assertEquals("Access Denied", JsonPath.read(response.body(), "$.message"));
    }

    @Test
    void unknownPathIsNotFoundForAuthenticatedCaller() throws Exception {
        assertEquals(404, send("GET", "/api/v1/does-not-exist", token, null).statusCode());
    }

    @Test
    void missingCredentialsIsUnauthorized() throws Exception {
        assertEquals(401, send("GET", "/api/v1/bucket/", null, null).statusCode());
    }

    private HttpResponse<String> send(String method, String path, String bearer, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/storage/minis3" + path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

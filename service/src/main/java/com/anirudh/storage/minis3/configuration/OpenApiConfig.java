package com.anirudh.storage.minis3.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI document served at /v3/api-docs, browsable at /swagger-ui.html.
 * <p>
 * Both credentials are Bearer tokens: a login JWT (eyJ...) or an API key (ms3_...). Use "Authorize" in Swagger UI
 * and paste either one.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearer";

    @Bean
    public OpenAPI miniS3OpenApi(@Value("${minis3.presign.base-url:}") String publicUrl) {
        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title("miniS3")
                        .version("v1 / v2")
                        .description("""
                                Self-hosted object storage. Authenticate with a login token from \
                                POST /api/v1/auth/login, or with an API key (ms3_...) from POST /api/v1/keys. \
                                v1 addresses files by UUID; v2 by bucket name and object key."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Login JWT (eyJ...) or API key (ms3_...)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));

        // behind 'tailscale serve' the app only sees plain HTTP; advertise the public HTTPS URL so "Try it out" works
        if (!publicUrl.isBlank()) {
            openApi.servers(List.of(new Server().url(publicUrl).description("public URL")));
        }
        return openApi;
    }
}

package com.anirudh.storage.minis3.client.autoconfigure;

import com.anirudh.storage.minis3.client.MiniS3Client;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers a shared {@link MiniS3Client} when {@code minis3.client.endpoint} is set,
 * unless the application defines its own.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@AutoConfiguration
@ConditionalOnClass(MiniS3Client.class)
@ConditionalOnProperty(prefix = "minis3.client", name = "endpoint")
@EnableConfigurationProperties(MiniS3ClientProperties.class)
public class MiniS3ClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MiniS3Client miniS3Client(MiniS3ClientProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("minis3.client.api-key must be set when minis3.client.endpoint is set");
        }
        return MiniS3Client.builder()
                .endpoint(properties.getEndpoint())
                .apiKey(properties.getApiKey())
                .connectTimeout(properties.getConnectTimeout())
                .requestTimeout(properties.getRequestTimeout())
                .build();
    }
}

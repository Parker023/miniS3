package com.anirudh.storage.minis3.client.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * <pre>
 * minis3:
 *   client:
 *     endpoint: https://laptop.tailnet.ts.net/storage/minis3
 *     api-key: ${MINIS3_API_KEY}
 * </pre>
 *
 * @author Anirudh
 * @since 19/10/26
 */
@ConfigurationProperties(prefix = "minis3.client")
public class MiniS3ClientProperties {

    /**
     * Base URL of the miniS3 service, including its context path. The client bean is only created when set.
     */
    private URI endpoint;

    /**
     * API key (ms3_...) created with POST /api/v1/keys.
     */
    private String apiKey;

    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * Time to wait for response headers; unset means no limit.
     */
    private Duration requestTimeout;

    public URI getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(URI endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}

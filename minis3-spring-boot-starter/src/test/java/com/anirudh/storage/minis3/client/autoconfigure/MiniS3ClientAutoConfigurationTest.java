package com.anirudh.storage.minis3.client.autoconfigure;

import com.anirudh.storage.minis3.client.MiniS3Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Anirudh
 * @since 19/10/26
 */
class MiniS3ClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MiniS3ClientAutoConfiguration.class));

    @Test
    void createsClientWhenEndpointAndKeyAreSet() {
        contextRunner
                .withPropertyValues(
                        "minis3.client.endpoint=http://localhost:2399/storage/minis3",
                        "minis3.client.api-key=ms3_0123456789ab_secret",
                        "minis3.client.request-timeout=30s")
                .run(context -> assertThat(context).hasSingleBean(MiniS3Client.class));
    }

    @Test
    void noClientWithoutEndpoint() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(MiniS3Client.class));
    }

    @Test
    void failsFastWhenEndpointSetButKeyMissing() {
        contextRunner
                .withPropertyValues("minis3.client.endpoint=http://localhost:2399/storage/minis3")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("minis3.client.api-key"));
    }

    @Test
    void backsOffWhenApplicationDefinesItsOwnClient() {
        MiniS3Client custom = MiniS3Client.builder().endpoint("http://other").apiKey("ms3_custom").build();
        contextRunner
                .withPropertyValues("minis3.client.endpoint=http://localhost:2399/storage/minis3", "minis3.client.api-key=x")
                .withBean(MiniS3Client.class, () -> custom)
                .run(context -> assertThat(context).getBean(MiniS3Client.class).isSameAs(custom));
    }
}

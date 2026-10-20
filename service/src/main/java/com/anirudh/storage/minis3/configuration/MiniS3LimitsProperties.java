package com.anirudh.storage.minis3.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Safeguards for running on the public internet. See minis3.limits.* in application.yaml.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "minis3.limits")
public class MiniS3LimitsProperties {

    /**
     * Required to register when set; empty means anyone who can reach the server can register.
     */
    private String inviteCode = "";

    /**
     * Failed logins per email before it is locked out.
     */
    private int loginMaxFailures = 5;

    /**
     * How long the failure window and the lockout last.
     */
    private Duration loginLockout = Duration.ofMinutes(15);

    /**
     * Total stored bytes per user across all buckets; 0 means unlimited.
     */
    private DataSize userQuota = DataSize.ofGigabytes(5);
}

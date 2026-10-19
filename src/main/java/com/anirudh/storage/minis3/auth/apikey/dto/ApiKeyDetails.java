package com.anirudh.storage.minis3.auth.apikey.dto;

import com.anirudh.storage.minis3.constants.ApiKeyScope;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Data
public class ApiKeyDetails {
    private UUID keyId;
    private String name;
    private String prefix;
    private Set<ApiKeyScope> scopes;
    private Set<UUID> bucketIds;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private boolean active;
}

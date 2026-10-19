package com.anirudh.storage.minis3.auth.apikey.entity;

import com.anirudh.storage.minis3.constants.ApiKeyScope;
import com.anirudh.storage.minis3.entity.BaseEntity;
import com.anirudh.storage.minis3.util.CommonUtils;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Entity
@Getter
@Setter
@Table(name = "api_keys")
public class ApiKey extends BaseEntity {

    public ApiKey() {
        this.keyPublicId = CommonUtils.generateRandomUUID();
    }

    @Column(nullable = false, unique = true)
    private UUID keyPublicId;

    @Column(nullable = false)
    private String name;

    // public, non-secret part of the key used for lookup
    @Column(nullable = false, unique = true)
    private String prefix;

    // SHA-256 (hex) of the secret part; the raw secret is never stored
    @Column(nullable = false)
    private String secretHash;

    @Column(nullable = false)
    private UUID ownerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_scopes", joinColumns = @JoinColumn(name = "api_key_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private Set<ApiKeyScope> scopes = new HashSet<>();

    // empty means the key can access every bucket of its owner
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_buckets", joinColumns = @JoinColumn(name = "api_key_id"))
    @Column(name = "bucket_public_id", nullable = false)
    private Set<UUID> bucketIds = new HashSet<>();

    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private LocalDateTime lastUsedAt;

    public boolean isActive() {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
}

package com.anirudh.storage.minis3.auth.dto;

import com.anirudh.storage.minis3.constants.ApiKeyScope;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Principal stored in the security context, for both JWT (human) and API key (service) callers.
 *
 * @author Anirudh
 * @since 25/02/26
 */
@Getter
@ToString
@EqualsAndHashCode
public class AuthenticatedUser {

    public static final String ROLE_USER = "ROLE_USER";

    private final UUID userPublicId;
    private final Set<ApiKeyScope> scopes;
    // empty means every bucket the user owns
    private final Set<UUID> allowedBucketIds;
    private final boolean apiKey;
    // keyPublicId of the API key used, null for JWT callers
    private final UUID apiKeyId;

    /**
     * A user logged in with a JWT: every scope, every bucket they own.
     */
    public AuthenticatedUser(UUID userPublicId) {
        this(userPublicId, EnumSet.allOf(ApiKeyScope.class), Set.of(), null);
    }

    private AuthenticatedUser(UUID userPublicId, Set<ApiKeyScope> scopes, Set<UUID> allowedBucketIds, UUID apiKeyId) {
        this.userPublicId = userPublicId;
        this.scopes = Set.copyOf(scopes);
        this.allowedBucketIds = Set.copyOf(allowedBucketIds);
        this.apiKey = apiKeyId != null;
        this.apiKeyId = apiKeyId;
    }

    public static AuthenticatedUser forApiKey(UUID apiKeyId, UUID ownerId, Set<ApiKeyScope> scopes, Set<UUID> allowedBucketIds) {
        return new AuthenticatedUser(ownerId, scopes, allowedBucketIds, apiKeyId);
    }

    public boolean canAccessBucket(UUID bucketPublicId) {
        return allowedBucketIds.isEmpty() || allowedBucketIds.contains(bucketPublicId);
    }

    /**
     * ROLE_USER only for JWT callers (needed to manage API keys), plus one SCOPE_* authority per scope.
     */
    public List<GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (!apiKey) {
            authorities.add(new SimpleGrantedAuthority(ROLE_USER));
        }
        scopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority(scope.getAuthority())));
        return authorities;
    }
}

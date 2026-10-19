package com.anirudh.storage.minis3.auth.apikey.service;

import com.anirudh.storage.minis3.auth.apikey.dto.CreateApiKeyRequest;
import com.anirudh.storage.minis3.auth.apikey.dto.GeneratedApiKey;
import com.anirudh.storage.minis3.auth.apikey.entity.ApiKey;
import com.anirudh.storage.minis3.auth.apikey.repository.ApiKeyRepository;
import com.anirudh.storage.minis3.auth.dto.AuthenticatedUser;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.exception.BucketAccessDeniedException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.anirudh.storage.minis3.constants.MiniS3Status.API_KEY;

/**
 * Issues, lists, revokes and verifies API keys.
 * <p>
 * Key format: {@code ms3_<prefix>_<secret>} where prefix is 12 hex chars (stored in clear, used for lookup)
 * and secret is 32 random bytes, base64url encoded (only its SHA-256 hash is stored).
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final int PREFIX_BYTES = 6;
    private static final int PREFIX_LENGTH = PREFIX_BYTES * 2;
    private static final int SECRET_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final BucketStoreRepository bucketStoreRepository;

    @Transactional
    public GeneratedApiKey createKey(CreateApiKeyRequest request) {
        UUID ownerId = CommonUtils.getUserIdFromSecurityContext();
        Set<UUID> bucketIds = request.getBucketIds() == null ? Set.of() : request.getBucketIds();

        if (!bucketIds.isEmpty() && bucketStoreRepository.countByOwnerIdAndBucketPublicIdIn(ownerId, bucketIds) != bucketIds.size()) {
            log.error("user {} tried to create a key for buckets they don't own", ownerId);
            throw new BucketAccessDeniedException("Bucket Access Denied");
        }

        String prefix = HexFormat.of().formatHex(randomBytes(PREFIX_BYTES));
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(SECRET_BYTES));

        ApiKey apiKey = new ApiKey();
        apiKey.setName(request.getName());
        apiKey.setOwnerId(ownerId);
        apiKey.setPrefix(prefix);
        apiKey.setSecretHash(hash(secret));
        apiKey.getScopes().addAll(request.getScopes());
        apiKey.getBucketIds().addAll(bucketIds);
        if (request.getExpiresInDays() != null) {
            apiKey.setExpiresAt(LocalDateTime.now().plusDays(request.getExpiresInDays()));
        }

        apiKeyRepository.save(apiKey);
        log.info("api key {} created for user {}", prefix, ownerId);

        return new GeneratedApiKey(apiKey, API_KEY.getValue() + prefix + "_" + secret);
    }

    public Page<ApiKey> getKeys(Pageable pageable) {
        UUID ownerId = CommonUtils.getUserIdFromSecurityContext();
        return apiKeyRepository.findByOwnerId(ownerId, pageable);
    }

    @Transactional
    public ApiKey revokeKey(UUID keyPublicId) {
        UUID ownerId = CommonUtils.getUserIdFromSecurityContext();
        ApiKey apiKey = apiKeyRepository.findByKeyPublicIdAndOwnerId(keyPublicId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Api key not found"));

        if (apiKey.getRevokedAt() == null) {
            apiKey.setRevokedAt(LocalDateTime.now());
            log.info("api key {} revoked", apiKey.getPrefix());
        }
        return apiKey;
    }

    /**
     * @return the principal for a valid, active key; empty for anything malformed, unknown, revoked or expired
     */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawKey) {
        String keyStart = API_KEY.getValue();
        int separatorIndex = keyStart.length() + PREFIX_LENGTH;

        if (rawKey == null || !rawKey.startsWith(keyStart) || rawKey.length() <= separatorIndex + 1
                || rawKey.charAt(separatorIndex) != '_') {
            return Optional.empty();
        }

        String prefix = rawKey.substring(keyStart.length(), separatorIndex);
        String secret = rawKey.substring(separatorIndex + 1);

        return apiKeyRepository.findByPrefix(prefix)
                .filter(apiKey -> MessageDigest.isEqual(
                        hash(secret).getBytes(StandardCharsets.UTF_8),
                        apiKey.getSecretHash().getBytes(StandardCharsets.UTF_8)))
                .filter(ApiKey::isActive)
                .map(apiKey -> {
                    apiKey.setLastUsedAt(LocalDateTime.now());
                    return AuthenticatedUser.forApiKey(apiKey.getOwnerId(), apiKey.getScopes(), apiKey.getBucketIds());
                });
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

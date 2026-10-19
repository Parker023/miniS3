package com.anirudh.storage.minis3.file.service;

import com.anirudh.storage.minis3.auth.apikey.entity.ApiKey;
import com.anirudh.storage.minis3.auth.apikey.repository.ApiKeyRepository;
import com.anirudh.storage.minis3.auth.dto.AuthenticatedUser;
import com.anirudh.storage.minis3.auth.jwt.properties.JwtProperties;
import com.anirudh.storage.minis3.bucket.auth.BucketAuthService;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.constants.PresignMethod;
import com.anirudh.storage.minis3.exception.InvalidRequestException;
import com.anirudh.storage.minis3.exception.PresignedUrlException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.file.dto.PresignedUrlResponse;
import com.anirudh.storage.minis3.util.CommonUtils;
import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and verifies pre-signed URLs: time-limited links that allow one method (GET or PUT) on one object
 * without an Authorization header.
 * <p>
 * The signature is HMAC-SHA256 over method, bucket, key, expiry and issuing API key, with a key derived from
 * the JWT secret. URLs issued with an API key stop working as soon as that key is revoked or expires.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Slf4j
@Service
public class PresignService {

    public static final Duration DEFAULT_EXPIRY = Duration.ofMinutes(15);
    public static final Duration MAX_EXPIRY = Duration.ofDays(7);
    private static final String HMAC = "HmacSHA256";
    // separates the pre-signing key from the JWT signing key derived from the same secret
    private static final String KEY_DERIVATION_LABEL = "minis3-presigned-url-v1";

    private final SecretKeySpec signingKey;
    private final Clock clock;
    private final BucketAuthService bucketAuthService;
    private final BucketStoreRepository bucketStoreRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final String baseUrl;

    public PresignService(JwtProperties jwtProperties,
                          Clock clock,
                          BucketAuthService bucketAuthService,
                          BucketStoreRepository bucketStoreRepository,
                          ApiKeyRepository apiKeyRepository,
                          @Value("${minis3.presign.base-url:}") String baseUrl) {
        byte[] masterKey = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.signingKey = new SecretKeySpec(hmac(new SecretKeySpec(masterKey, HMAC), KEY_DERIVATION_LABEL), HMAC);
        this.clock = clock;
        this.bucketAuthService = bucketAuthService;
        this.bucketStoreRepository = bucketStoreRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.baseUrl = baseUrl;
    }

    public PresignedUrlResponse presign(String bucketName, String key, PresignMethod method, Long expiresInSeconds) {
        ObjectService.validateKey(key);
        Duration expiry = expiresInSeconds == null ? DEFAULT_EXPIRY : Duration.ofSeconds(expiresInSeconds);
        if (expiry.isNegative() || expiry.isZero() || expiry.compareTo(MAX_EXPIRY) > 0) {
            throw new InvalidRequestException("expiresInSeconds must be between 1 and " + MAX_EXPIRY.toSeconds());
        }

        AuthenticatedUser user = CommonUtils.getAuthenticatedUser();
        if (!user.getScopes().contains(method.getRequiredScope())) {
            throw new AccessDeniedException("Missing scope " + method.getRequiredScope());
        }
        BucketStore bucketStore = bucketAuthService.resolveAccessibleBucket(bucketName);

        Instant expiresAt = clock.instant().plus(expiry);
        long expires = expiresAt.getEpochSecond();
        UUID credential = user.getApiKeyId();
        String signature = sign(method, bucketStore.getBucketPublicId(), key, expires, credential);

        UriComponentsBuilder builder = baseUrl.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath()
                : UriComponentsBuilder.fromUriString(baseUrl);
        builder.path("/api/v2/presigned/" + bucketStore.getBucketPublicId() + "/" + encodeKey(key))
                .queryParam("expires", expires)
                .queryParam("signature", signature);
        if (credential != null) {
            builder.queryParam("credential", credential);
        }

        log.info("pre-signed {} url issued for {} in bucket {}, expires {}", method, key, bucketName, expiresAt);
        return new PresignedUrlResponse(builder.build(true).toUriString(), method, Instant.ofEpochSecond(expires));
    }

    /**
     * @return the bucket the URL grants access to
     * @throws PresignedUrlException if the signature is wrong, the URL expired, or the issuing key is no longer valid
     */
    public BucketStore verify(PresignMethod method, UUID bucketPublicId, String key, long expires, UUID credential, String signature) {
        ObjectService.validateKey(key);
        if (signature == null) {
            throw new PresignedUrlException("Missing signature");
        }

        String expected = sign(method, bucketPublicId, key, expires, credential);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new PresignedUrlException("Invalid signature");
        }
        if (clock.instant().getEpochSecond() > expires) {
            throw new PresignedUrlException("Pre-signed URL expired");
        }

        BucketStore bucketStore = bucketStoreRepository.findByBucketPublicId(bucketPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not Found"));

        if (credential != null && !issuingKeyStillAllows(credential, bucketStore, method)) {
            throw new PresignedUrlException("Pre-signed URL is no longer valid");
        }
        return bucketStore;
    }

    private boolean issuingKeyStillAllows(UUID keyPublicId, BucketStore bucketStore, PresignMethod method) {
        return apiKeyRepository.findByKeyPublicId(keyPublicId)
                .filter(ApiKey::isActive)
                .filter(apiKey -> apiKey.getOwnerId().equals(bucketStore.getOwnerId()))
                .filter(apiKey -> apiKey.getScopes().contains(method.getRequiredScope()))
                .filter(apiKey -> apiKey.getBucketIds().isEmpty() || apiKey.getBucketIds().contains(bucketStore.getBucketPublicId()))
                .isPresent();
    }

    private String sign(PresignMethod method, UUID bucketPublicId, String key, long expires, UUID credential) {
        String stringToSign = String.join("\n",
                method.name(),
                bucketPublicId.toString(),
                key,
                Long.toString(expires),
                credential == null ? "" : credential.toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(signingKey, stringToSign));
    }

    private static byte[] hmac(SecretKeySpec key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    // encode each segment but keep the slashes, so the key round-trips through {*key}
    private static String encodeKey(String key) {
        return Arrays.stream(key.split("/", -1))
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }
}

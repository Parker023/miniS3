package com.anirudh.storage.minis3.file.service;

import com.anirudh.storage.minis3.auth.apikey.entity.ApiKey;
import com.anirudh.storage.minis3.auth.apikey.repository.ApiKeyRepository;
import com.anirudh.storage.minis3.auth.dto.AuthenticatedUser;
import com.anirudh.storage.minis3.auth.jwt.properties.JwtProperties;
import com.anirudh.storage.minis3.bucket.auth.BucketAuthService;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.constants.ApiKeyScope;
import com.anirudh.storage.minis3.constants.PresignMethod;
import com.anirudh.storage.minis3.exception.InvalidRequestException;
import com.anirudh.storage.minis3.exception.PresignedUrlException;
import com.anirudh.storage.minis3.file.dto.PresignedUrlResponse;
import com.anirudh.storage.minis3.util.CommonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@ExtendWith(MockitoExtension.class)
class PresignServiceTest {

    private static final String SECRET = "c2VjcmV0LWtleS1mb3ItbWluaXMzLWludGVncmF0aW9uLXRlc3RzIQ==";
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    @Mock
    private BucketAuthService bucketAuthService;
    @Mock
    private BucketStoreRepository bucketStoreRepository;
    @Mock
    private ApiKeyRepository apiKeyRepository;

    private BucketStore bucketStore;
    private UUID ownerId;

    @BeforeEach
    void setup() {
        ownerId = CommonUtils.generateRandomUUID();
        bucketStore = new BucketStore();
        bucketStore.setBucketName("photos");
        bucketStore.setOwnerId(ownerId);
        loginAs(new AuthenticatedUser(ownerId));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void presignedUrlVerifiesUntilExpiry() {
        when(bucketAuthService.resolveAccessibleBucket("photos")).thenReturn(bucketStore);
        when(bucketStoreRepository.findByBucketPublicId(bucketStore.getBucketPublicId())).thenReturn(Optional.of(bucketStore));

        PresignedUrlResponse response = service(NOW).presign("photos", "2026/my cat.png", PresignMethod.GET, 600L);
        Signed signed = parse(response.getUrl());

        assertEquals(NOW.plusSeconds(600), response.getExpiresAt());
        assertEquals("/api/v2/presigned/" + bucketStore.getBucketPublicId() + "/2026/my%20cat.png", signed.uri().getRawPath());

        // still valid on the last second, rejected one second later
        assertSame(bucketStore, service(NOW.plusSeconds(600)).verify(PresignMethod.GET, signed.bucketId(), "2026/my cat.png", signed.expires(), null, signed.signature()));
        assertThrows(PresignedUrlException.class,
                () -> service(NOW.plusSeconds(601)).verify(PresignMethod.GET, signed.bucketId(), "2026/my cat.png", signed.expires(), null, signed.signature()));
    }

    @Test
    void anyTamperingBreaksTheSignature() {
        when(bucketAuthService.resolveAccessibleBucket("photos")).thenReturn(bucketStore);
        PresignService service = service(NOW);
        Signed signed = parse(service.presign("photos", "a.txt", PresignMethod.GET, null).getUrl());

        assertThrows(PresignedUrlException.class, () -> service.verify(PresignMethod.PUT, signed.bucketId(), "a.txt", signed.expires(), null, signed.signature()));
        assertThrows(PresignedUrlException.class, () -> service.verify(PresignMethod.GET, signed.bucketId(), "b.txt", signed.expires(), null, signed.signature()));
        assertThrows(PresignedUrlException.class, () -> service.verify(PresignMethod.GET, UUID.randomUUID(), "a.txt", signed.expires(), null, signed.signature()));
        assertThrows(PresignedUrlException.class, () -> service.verify(PresignMethod.GET, signed.bucketId(), "a.txt", signed.expires() + 3600, null, signed.signature()));
        assertThrows(PresignedUrlException.class, () -> service.verify(PresignMethod.GET, signed.bucketId(), "a.txt", signed.expires(), UUID.randomUUID(), signed.signature()));
        assertThrows(PresignedUrlException.class, () -> service.verify(PresignMethod.GET, signed.bucketId(), "a.txt", signed.expires(), null, null));
    }

    @Test
    void urlIssuedWithApiKeyDiesWithTheKey() {
        ApiKey apiKey = new ApiKey();
        apiKey.setOwnerId(ownerId);
        apiKey.getScopes().add(ApiKeyScope.READ);
        loginAs(AuthenticatedUser.forApiKey(apiKey.getKeyPublicId(), ownerId, Set.of(ApiKeyScope.READ), Set.of()));
        when(bucketAuthService.resolveAccessibleBucket("photos")).thenReturn(bucketStore);
        when(bucketStoreRepository.findByBucketPublicId(bucketStore.getBucketPublicId())).thenReturn(Optional.of(bucketStore));
        when(apiKeyRepository.findByKeyPublicId(apiKey.getKeyPublicId())).thenReturn(Optional.of(apiKey));

        PresignService service = service(NOW);
        Signed signed = parse(service.presign("photos", "a.txt", PresignMethod.GET, null).getUrl());
        assertEquals(apiKey.getKeyPublicId(), signed.credential());

        assertSame(bucketStore, service.verify(PresignMethod.GET, signed.bucketId(), "a.txt", signed.expires(), signed.credential(), signed.signature()));

        apiKey.setRevokedAt(LocalDateTime.now());
        assertThrows(PresignedUrlException.class,
                () -> service.verify(PresignMethod.GET, signed.bucketId(), "a.txt", signed.expires(), signed.credential(), signed.signature()));
    }

    @Test
    void presignNeedsMatchingScopeAndSaneExpiry() {
        loginAs(AuthenticatedUser.forApiKey(UUID.randomUUID(), ownerId, Set.of(ApiKeyScope.READ), Set.of()));
        PresignService service = service(NOW);

        assertThrows(AccessDeniedException.class, () -> service.presign("photos", "a.txt", PresignMethod.PUT, null));
        assertThrows(InvalidRequestException.class, () -> service.presign("photos", "a.txt", PresignMethod.GET, 0L));
        assertThrows(InvalidRequestException.class,
                () -> service.presign("photos", "a.txt", PresignMethod.GET, Duration.ofDays(8).toSeconds()));
    }

    private PresignService service(Instant now) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        return new PresignService(jwtProperties, Clock.fixed(now, ZoneOffset.UTC), bucketAuthService,
                bucketStoreRepository, apiKeyRepository, "http://minis3.test/storage/minis3");
    }

    private void loginAs(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private record Signed(URI uri, UUID bucketId, long expires, UUID credential, String signature) {
    }

    private Signed parse(String url) {
        URI uri = URI.create(url);
        UriComponents components = UriComponentsBuilder.fromUri(uri).build();
        String path = uri.getRawPath().substring("/storage/minis3".length());
        String credential = components.getQueryParams().getFirst("credential");
        return new Signed(
                URI.create(path),
                UUID.fromString(path.split("/")[4]),
                Long.parseLong(components.getQueryParams().getFirst("expires")),
                credential == null ? null : UUID.fromString(credential),
                components.getQueryParams().getFirst("signature"));
    }
}

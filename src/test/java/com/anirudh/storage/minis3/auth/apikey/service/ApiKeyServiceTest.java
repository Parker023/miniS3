package com.anirudh.storage.minis3.auth.apikey.service;

import com.anirudh.storage.minis3.auth.apikey.dto.CreateApiKeyRequest;
import com.anirudh.storage.minis3.auth.apikey.dto.GeneratedApiKey;
import com.anirudh.storage.minis3.auth.apikey.entity.ApiKey;
import com.anirudh.storage.minis3.auth.apikey.repository.ApiKeyRepository;
import com.anirudh.storage.minis3.auth.dto.AuthenticatedUser;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.constants.ApiKeyScope;
import com.anirudh.storage.minis3.exception.BucketAccessDeniedException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.util.CommonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private BucketStoreRepository bucketStoreRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private UUID ownerId;
    private CreateApiKeyRequest request;

    @BeforeEach
    void setup() {
        ownerId = CommonUtils.generateRandomUUID();
        AuthenticatedUser user = new AuthenticatedUser(ownerId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        request = new CreateApiKeyRequest();
        request.setName("invoice-service");
        request.setScopes(Set.of(ApiKeyScope.READ, ApiKeyScope.WRITE));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private GeneratedApiKey createKey() {
        when(apiKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return apiKeyService.createKey(request);
    }

    @Test
    void createKey_returnsRawKeyOnce_andStoresOnlyHash() {
        GeneratedApiKey generated = createKey();

        assertTrue(generated.rawKey().matches("ms3_[0-9a-f]{12}_[A-Za-z0-9_-]{43}"));
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();

        assertEquals(ownerId, saved.getOwnerId());
        assertEquals(Set.of(ApiKeyScope.READ, ApiKeyScope.WRITE), saved.getScopes());
        assertTrue(generated.rawKey().contains(saved.getPrefix()));
        assertFalse(generated.rawKey().contains(saved.getSecretHash()));
        assertNull(saved.getExpiresAt());
    }

    @Test
    void createKey_withExpiry() {
        request.setExpiresInDays(30);

        GeneratedApiKey generated = createKey();

        assertTrue(generated.apiKey().getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void createKey_forBucketNotOwned_throws() {
        UUID bucketId = CommonUtils.generateRandomUUID();
        request.setBucketIds(Set.of(bucketId));
        when(bucketStoreRepository.countByOwnerIdAndBucketPublicIdIn(ownerId, Set.of(bucketId))).thenReturn(0L);

        assertThrows(BucketAccessDeniedException.class, () -> apiKeyService.createKey(request));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void authenticate_validKey() {
        UUID bucketId = CommonUtils.generateRandomUUID();
        request.setBucketIds(Set.of(bucketId));
        when(bucketStoreRepository.countByOwnerIdAndBucketPublicIdIn(ownerId, Set.of(bucketId))).thenReturn(1L);
        GeneratedApiKey generated = createKey();
        when(apiKeyRepository.findByPrefix(generated.apiKey().getPrefix())).thenReturn(Optional.of(generated.apiKey()));

        AuthenticatedUser user = apiKeyService.authenticate(generated.rawKey()).orElseThrow();

        assertEquals(ownerId, user.getUserPublicId());
        assertTrue(user.isApiKey());
        assertEquals(Set.of(ApiKeyScope.READ, ApiKeyScope.WRITE), user.getScopes());
        assertTrue(user.canAccessBucket(bucketId));
        assertFalse(user.canAccessBucket(CommonUtils.generateRandomUUID()));
        assertNotNull(generated.apiKey().getLastUsedAt());
    }

    @Test
    void authenticate_wrongSecret() {
        GeneratedApiKey generated = createKey();
        when(apiKeyRepository.findByPrefix(generated.apiKey().getPrefix())).thenReturn(Optional.of(generated.apiKey()));

        String tampered = generated.rawKey().substring(0, generated.rawKey().length() - 1)
                + (generated.rawKey().endsWith("A") ? "B" : "A");

        assertTrue(apiKeyService.authenticate(tampered).isEmpty());
    }

    @Test
    void authenticate_revokedKey() {
        GeneratedApiKey generated = createKey();
        generated.apiKey().setRevokedAt(LocalDateTime.now());
        when(apiKeyRepository.findByPrefix(generated.apiKey().getPrefix())).thenReturn(Optional.of(generated.apiKey()));

        assertTrue(apiKeyService.authenticate(generated.rawKey()).isEmpty());
    }

    @Test
    void authenticate_expiredKey() {
        GeneratedApiKey generated = createKey();
        generated.apiKey().setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(apiKeyRepository.findByPrefix(generated.apiKey().getPrefix())).thenReturn(Optional.of(generated.apiKey()));

        assertTrue(apiKeyService.authenticate(generated.rawKey()).isEmpty());
    }

    @Test
    void authenticate_malformedKey() {
        assertTrue(apiKeyService.authenticate(null).isEmpty());
        assertTrue(apiKeyService.authenticate("ms3_short").isEmpty());
        assertTrue(apiKeyService.authenticate("ms3_0123456789abXsecret").isEmpty());
        assertTrue(apiKeyService.authenticate("eyJhbGciOiJIUzI1NiJ9.x.y").isEmpty());
        verify(apiKeyRepository, never()).findByPrefix(anyString());
    }

    @Test
    void revokeKey() {
        ApiKey apiKey = new ApiKey();
        when(apiKeyRepository.findByKeyPublicIdAndOwnerId(apiKey.getKeyPublicId(), ownerId)).thenReturn(Optional.of(apiKey));

        ApiKey revoked = apiKeyService.revokeKey(apiKey.getKeyPublicId());

        assertNotNull(revoked.getRevokedAt());
        assertFalse(revoked.isActive());
    }

    @Test
    void revokeKey_notFound() {
        UUID keyId = CommonUtils.generateRandomUUID();
        when(apiKeyRepository.findByKeyPublicIdAndOwnerId(keyId, ownerId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> apiKeyService.revokeKey(keyId));
    }
}

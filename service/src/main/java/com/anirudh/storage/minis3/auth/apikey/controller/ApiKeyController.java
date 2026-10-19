package com.anirudh.storage.minis3.auth.apikey.controller;

import com.anirudh.storage.minis3.auth.apikey.dto.ApiKeyDetails;
import com.anirudh.storage.minis3.auth.apikey.dto.CreateApiKeyRequest;
import com.anirudh.storage.minis3.auth.apikey.dto.CreateApiKeyResponse;
import com.anirudh.storage.minis3.auth.apikey.dto.GeneratedApiKey;
import com.anirudh.storage.minis3.auth.apikey.mapper.ApiKeyMapper;
import com.anirudh.storage.minis3.auth.apikey.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Key management. Only reachable with a JWT (ROLE_USER, see SecurityConfig): an API key cannot mint other keys.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ApiKeyMapper apiKeyMapper;

    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> createKey(@RequestBody @Valid CreateApiKeyRequest request) {
        GeneratedApiKey generated = apiKeyService.createKey(request);
        CreateApiKeyResponse response = apiKeyMapper.toCreateResponse(generated);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{keyId}")
                .buildAndExpand(response.getKeyId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ApiKeyDetails>> getKeys(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(apiKeyService.getKeys(pageable).map(apiKeyMapper::toDetails));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<ApiKeyDetails> revokeKey(@PathVariable("keyId") UUID keyId) {
        return ResponseEntity.ok(apiKeyMapper.toDetails(apiKeyService.revokeKey(keyId)));
    }
}

package com.anirudh.storage.minis3.auth.apikey.mapper;

import com.anirudh.storage.minis3.auth.apikey.dto.ApiKeyDetails;
import com.anirudh.storage.minis3.auth.apikey.dto.CreateApiKeyResponse;
import com.anirudh.storage.minis3.auth.apikey.dto.GeneratedApiKey;
import com.anirudh.storage.minis3.auth.apikey.entity.ApiKey;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Component
public class ApiKeyMapper {

    public ApiKeyDetails toDetails(ApiKey apiKey) {
        ApiKeyDetails details = new ApiKeyDetails();
        copy(apiKey, details);
        return details;
    }

    public CreateApiKeyResponse toCreateResponse(GeneratedApiKey generated) {
        CreateApiKeyResponse response = new CreateApiKeyResponse();
        copy(generated.apiKey(), response);
        response.setApiKey(generated.rawKey());
        response.setMessage("Store this key now, it will not be shown again");
        return response;
    }

    private void copy(ApiKey apiKey, ApiKeyDetails details) {
        details.setKeyId(apiKey.getKeyPublicId());
        details.setName(apiKey.getName());
        details.setPrefix(apiKey.getPrefix());
        details.setScopes(Set.copyOf(apiKey.getScopes()));
        details.setBucketIds(Set.copyOf(apiKey.getBucketIds()));
        details.setCreatedAt(apiKey.getCreatedAt());
        details.setExpiresAt(apiKey.getExpiresAt());
        details.setLastUsedAt(apiKey.getLastUsedAt());
        details.setRevokedAt(apiKey.getRevokedAt());
        details.setActive(apiKey.isActive());
    }
}

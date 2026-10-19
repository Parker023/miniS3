package com.anirudh.storage.minis3.auth.apikey.dto;

import com.anirudh.storage.minis3.constants.ApiKeyScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Data
public class CreateApiKeyRequest {

    @NotBlank(message = "key name cannot be blank")
    @Size(min = 3, max = 50)
    private String name;

    @NotEmpty(message = "at least one scope is required")
    private Set<ApiKeyScope> scopes;

    // optional: restrict the key to these buckets; empty or null means all of the owner's buckets
    private Set<UUID> bucketIds;

    // optional: null means the key never expires
    @Positive
    @Max(365)
    private Integer expiresInDays;
}

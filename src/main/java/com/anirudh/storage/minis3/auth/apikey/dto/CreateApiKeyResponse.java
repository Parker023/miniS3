package com.anirudh.storage.minis3.auth.apikey.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Returned only once, on creation: the only response that carries the raw key.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateApiKeyResponse extends ApiKeyDetails {
    private String apiKey;
    private String message;
}

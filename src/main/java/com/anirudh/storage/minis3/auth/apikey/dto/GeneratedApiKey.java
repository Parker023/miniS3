package com.anirudh.storage.minis3.auth.apikey.dto;

import com.anirudh.storage.minis3.auth.apikey.entity.ApiKey;

/**
 * A freshly created key together with its raw value, which is never persisted.
 *
 * @author Anirudh
 * @since 19/10/26
 */
public record GeneratedApiKey(ApiKey apiKey, String rawKey) {
}

package com.anirudh.storage.minis3.constants;

import lombok.Getter;

/**
 * HTTP methods a pre-signed URL can be issued for, with the scope the issuer needs.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Getter
public enum PresignMethod {

    GET(ApiKeyScope.READ), PUT(ApiKeyScope.WRITE);

    private final ApiKeyScope requiredScope;

    PresignMethod(ApiKeyScope requiredScope) {
        this.requiredScope = requiredScope;
    }
}

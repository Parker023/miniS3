package com.anirudh.storage.minis3.constants;

/**
 * Permissions an API key can be granted. JWT-authenticated users implicitly hold all of them.
 *
 * @author Anirudh
 * @since 19/10/26
 */
public enum ApiKeyScope {

    READ, WRITE, DELETE;

    public String getAuthority() {
        return "SCOPE_" + name();
    }
}

package com.anirudh.storage.minis3.constants;

import lombok.Getter;

/**
 * @author Anirudh
 * @since 21/02/26
 */
@Getter
public enum MiniS3Status {

    SAVED("Saved"), REGISTERED("Registered"), LOGGEDIN("login success"), AUTH_FAILED("Authentication Failed"),
    FAILED("Failed"),BEARER("Bearer "), API_KEY("ms3_");

    private final String value;

    MiniS3Status(String saved) {
        this.value = saved;
    }

}

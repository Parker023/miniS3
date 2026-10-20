package com.anirudh.storage.minis3.exception;

import lombok.Getter;

import java.time.Duration;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Getter
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many failed logins, try again later");
        this.retryAfter = retryAfter;
    }
}

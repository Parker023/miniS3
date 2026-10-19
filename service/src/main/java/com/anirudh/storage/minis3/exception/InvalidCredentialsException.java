package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 22/02/26
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException() {

    }
}

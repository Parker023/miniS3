package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 19/10/26
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}

package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 25/02/26
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException() {

    }
}

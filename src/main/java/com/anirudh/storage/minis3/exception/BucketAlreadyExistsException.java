package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 24/02/26
 */
public class BucketAlreadyExistsException extends RuntimeException {
    public BucketAlreadyExistsException(String message) {
        super(message);
    }

    public BucketAlreadyExistsException() {

    }
}

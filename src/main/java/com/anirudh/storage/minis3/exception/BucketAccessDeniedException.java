package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 02/03/26
 */
public class BucketAccessDeniedException extends RuntimeException {
    public BucketAccessDeniedException(String message) {
        super(message);
    }

    public BucketAccessDeniedException() {

    }
}

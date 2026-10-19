package com.anirudh.storage.minis3.exception;

/**
 * A pre-signed URL that is tampered with, expired, or whose issuing API key no longer allows the request.
 *
 * @author Anirudh
 * @since 19/10/26
 */
public class PresignedUrlException extends RuntimeException {
    public PresignedUrlException(String message) {
        super(message);
    }
}

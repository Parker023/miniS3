package com.anirudh.storage.minis3.client;

/**
 * Any failure calling miniS3. {@link #statusCode()} is the HTTP status, or 0 when no response was received
 * (connection refused, timeout, interrupted).
 *
 * @author Anirudh
 * @since 19/10/26
 */
public class MiniS3Exception extends RuntimeException {

    private final int statusCode;

    public MiniS3Exception(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public MiniS3Exception(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int statusCode() {
        return statusCode;
    }
}

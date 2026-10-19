package com.anirudh.storage.minis3.client;

/**
 * 401 (missing, invalid, expired or revoked API key) or 403 (key lacks the scope or bucket access).
 *
 * @author Anirudh
 * @since 19/10/26
 */
public class MiniS3AccessDeniedException extends MiniS3Exception {
    public MiniS3AccessDeniedException(int statusCode, String message) {
        super(statusCode, message);
    }
}

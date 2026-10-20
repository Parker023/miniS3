package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 19/10/26
 */
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }
}

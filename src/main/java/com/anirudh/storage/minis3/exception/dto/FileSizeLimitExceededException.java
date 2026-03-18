package com.anirudh.storage.minis3.exception.dto;

/**
 * @author Anirudh
 * @since 04/03/26
 */
public class FileSizeLimitExceededException extends RuntimeException {
    public FileSizeLimitExceededException(String message) {
        super(message);
    }
}

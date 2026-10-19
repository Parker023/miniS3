package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 11/03/26
 */
public class FileAccessDeniedException extends RuntimeException {
    public FileAccessDeniedException(String message) {
        super(message);
    }
}

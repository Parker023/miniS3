package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 04/03/26
 */
public class EmptyFileException extends RuntimeException {
    public EmptyFileException(String message) {
        super(message);
    }
}

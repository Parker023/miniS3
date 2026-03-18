package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 02/03/26
 */
public class FileAlreadyExistsException extends RuntimeException {
    public FileAlreadyExistsException(String message) {
        super(message);
    }
}

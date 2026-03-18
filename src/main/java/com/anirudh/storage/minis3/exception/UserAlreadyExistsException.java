package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 21/02/26
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException() {

    }

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}

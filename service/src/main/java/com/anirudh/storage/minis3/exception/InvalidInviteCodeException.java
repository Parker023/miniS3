package com.anirudh.storage.minis3.exception;

/**
 * @author Anirudh
 * @since 19/10/26
 */
public class InvalidInviteCodeException extends RuntimeException {
    public InvalidInviteCodeException() {
        super("A valid invite code is required to register");
    }
}

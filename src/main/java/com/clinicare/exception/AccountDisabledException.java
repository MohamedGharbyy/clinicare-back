package com.clinicare.exception;

/**
 * Thrown during login when the account has been disabled by an Admin. Maps to
 * HTTP 403 Forbidden so clients can show a distinct "account disabled" message
 * rather than a generic invalid-credentials error.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("This account has been disabled by an administrator.");
    }
}

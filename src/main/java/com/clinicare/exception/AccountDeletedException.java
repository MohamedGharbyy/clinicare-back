package com.clinicare.exception;

/**
 * Thrown during login when the account has been soft-deleted by an Admin.
 * Maps to HTTP 403 Forbidden; the account can no longer authenticate and its
 * historical records remain intact.
 */
public class AccountDeletedException extends RuntimeException {

    public AccountDeletedException() {
        super("This account has been deleted.");
    }
}

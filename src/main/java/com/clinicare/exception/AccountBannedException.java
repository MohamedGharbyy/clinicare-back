package com.clinicare.exception;

import java.time.LocalDateTime;

/**
 * Thrown during login when the account is temporarily banned by an Admin.
 * Carries the ban expiry so clients can tell the user when access returns.
 * Maps to HTTP 403 Forbidden.
 */
public class AccountBannedException extends RuntimeException {

    private final LocalDateTime banExpiresAt;

    public AccountBannedException(LocalDateTime banExpiresAt) {
        super("This account is temporarily banned by an administrator.");
        this.banExpiresAt = banExpiresAt;
    }

    public LocalDateTime getBanExpiresAt() {
        return banExpiresAt;
    }
}

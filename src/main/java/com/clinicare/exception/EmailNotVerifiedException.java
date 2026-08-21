package com.clinicare.exception;

/**
 * Thrown during login when the account's email address has not yet been
 * confirmed. The account exists and the password may be correct, but access is
 * withheld until the user verifies their email. Maps to HTTP 403 Forbidden.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Please verify your email address before logging in. "
                + "Check your inbox for the confirmation link sent by CliniCare.");
    }
}

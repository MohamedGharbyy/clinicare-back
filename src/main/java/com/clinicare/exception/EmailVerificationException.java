package com.clinicare.exception;

/**
 * Thrown when an email verification token cannot be accepted: it is missing,
 * unknown, already used, or expired. Maps to HTTP 400 Bad Request. The message
 * is safe to show to the user and never discloses which email the token
 * belonged to or other account details.
 */
public class EmailVerificationException extends RuntimeException {

    public EmailVerificationException(String message) {
        super(message);
    }
}

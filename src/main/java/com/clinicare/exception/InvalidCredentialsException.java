package com.clinicare.exception;

/**
 * Thrown when a login attempt fails because either the email or the password
 * is wrong. Intentionally generic so clients cannot tell which one. Maps to
 * HTTP 401 Unauthorized.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
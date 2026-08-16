package com.clinicare.exception;

/**
 * Thrown when registration is attempted with an email that already exists
 * in the database. Maps to HTTP 409 Conflict.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    private final String email;

    public EmailAlreadyExistsException(String email) {
        super("Email is already registered: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
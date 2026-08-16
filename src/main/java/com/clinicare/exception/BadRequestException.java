package com.clinicare.exception;

/**
 * Thrown for request-level validation failures that are not covered by Bean
 * Validation. Maps to HTTP 400 Bad Request.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
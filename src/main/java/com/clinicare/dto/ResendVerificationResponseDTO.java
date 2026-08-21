package com.clinicare.dto;

/**
 * Result of a resend-verification request. {@code sent} is {@code true} only
 * when a code email was actually dispatched to an outstanding (unverified)
 * account. For unknown or already-verified addresses the caller still receives
 * a success-style response so the API does not reveal account existence.
 */
public record ResendVerificationResponseDTO(
        boolean sent,
        String message,
        Long retryAfterSeconds) {
}

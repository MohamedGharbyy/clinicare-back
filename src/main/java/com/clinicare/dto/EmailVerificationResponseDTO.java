package com.clinicare.dto;

/**
 * Result of an email verification attempt. Deliberately omits any sensitive
 * account data; it only reports whether verification succeeded and a short,
 * user-safe message.
 */
public record EmailVerificationResponseDTO(boolean verified, String message) {
}

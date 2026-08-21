package com.clinicare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /api/auth/resend-verification}. Identifies the account
 * by email so a fresh verification code can be issued and emailed. The response
 * deliberately avoids revealing whether the account exists or is already
 * verified, to prevent email enumeration.
 */
public record ResendVerificationRequestDTO(
        @NotBlank @Email String email) {
}

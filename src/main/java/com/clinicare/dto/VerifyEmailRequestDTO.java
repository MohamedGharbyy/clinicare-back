package com.clinicare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/auth/verify-email}. Identifies the account by the
 * email used at registration plus the 6-digit verification code sent by email.
 * The code is never echoed back in any response or log.
 */
public record VerifyEmailRequestDTO(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) @Pattern(regexp = "\\d{6}") String code) {
}

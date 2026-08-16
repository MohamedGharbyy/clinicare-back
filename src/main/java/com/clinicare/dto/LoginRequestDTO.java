package com.clinicare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload accepted by {@code POST /api/auth/login}.
 */
public record LoginRequestDTO(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 72, message = "password must not exceed 72 characters")
        String password) {
}

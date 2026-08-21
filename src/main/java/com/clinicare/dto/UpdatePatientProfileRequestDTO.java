package com.clinicare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /api/patient/profile}. Validated at the controller so
 * field-level messages are surfaced under {@code fields} in the error response.
 */
public record UpdatePatientProfileRequestDTO(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(max = 30, message = "Phone number must be at most 30 characters")
        String phoneNumber) {
}

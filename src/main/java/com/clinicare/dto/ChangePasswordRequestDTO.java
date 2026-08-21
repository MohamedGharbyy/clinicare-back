package com.clinicare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/patient/change-password}. The new password must be
 * at least 8 characters; matching the confirmation is enforced in the service
 * layer (a cross-field rule that bean validation cannot express here).
 */
public record ChangePasswordRequestDTO(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "New password must be at least 8 characters")
        String newPassword,

        @NotBlank(message = "Confirm new password is required")
        String confirmPassword) {
}

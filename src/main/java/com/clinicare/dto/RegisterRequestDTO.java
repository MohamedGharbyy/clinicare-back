package com.clinicare.dto;

import com.clinicare.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload accepted by {@code POST /api/auth/register}.
 * <p>Role-specific fields are optional (not cross-field required): for a
 * PATIENT the profile stores {@code dateOfBirth}/{@code phoneNumber}, and for
 * a DOCTOR the profile stores {@code specialty}/{@code licenseNumber}/
 * {@code phoneNumber}.
 */
public record RegisterRequestDTO(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must not exceed 255 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        @Size(max = 72, message = "password must not exceed 72 characters")
        String password,

        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must not exceed 100 characters")
        String lastName,

        @NotNull(message = "role is required")
        Role role,

        @Past(message = "dateOfBirth must be a date in the past")
        LocalDate dateOfBirth,

        @Size(max = 30, message = "phoneNumber must not exceed 30 characters")
        String phoneNumber,

        @Size(max = 100, message = "specialty must not exceed 100 characters")
        String specialty,

        @Size(max = 100, message = "licenseNumber must not exceed 100 characters")
        String licenseNumber) {
}
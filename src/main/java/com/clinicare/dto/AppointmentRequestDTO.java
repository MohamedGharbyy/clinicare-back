package com.clinicare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Payload for creating a new appointment request.
 * <p>
 * The patient is always derived from the authenticated user, so this payload
 * intentionally carries no patient identifier. New appointments are always
 * persisted with a {@code PENDING} status (see {@link com.clinicare.entity.AppointmentStatus}).
 */
public record AppointmentRequestDTO(
        @NotNull(message = "doctorId is required")
        Long doctorId,

        @NotNull(message = "appointmentDate is required")
        LocalDate appointmentDate,

        @NotNull(message = "appointmentTime is required")
        LocalTime appointmentTime,

        @NotBlank(message = "reason is required")
        @Size(max = 255, message = "reason must not exceed 255 characters")
        String reason,

        @Size(max = 2000, message = "notes must not exceed 2000 characters")
        String notes) {
}

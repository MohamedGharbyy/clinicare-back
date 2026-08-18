package com.clinicare.dto;

import com.clinicare.entity.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Read-only representation of an {@code Appointment} returned by the service
 * layer.
 */
public record AppointmentResponseDTO(
        Long id,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        String doctorSpecialty,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String reason,
        String notes,
        AppointmentStatus status,
        LocalDateTime createdAt) {
}

package com.clinicare.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Doctor-facing representation of a patient they have an established
 * appointment relationship with.
 */
public record DoctorPatientResponseDTO(
        Long id,
        String name,
        String email,
        LocalDate dateOfBirth,
        String phoneNumber,
        LocalDateTime registeredAt) {
}

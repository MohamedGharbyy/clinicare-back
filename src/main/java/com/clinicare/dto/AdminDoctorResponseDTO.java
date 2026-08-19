package com.clinicare.dto;

import java.time.LocalDateTime;

/**
 * Admin-only representation of a doctor account. Richer than the public
 * {@link DoctorResponseDTO}: it includes the linked user's email and the
 * registration-related profile fields used for management oversight.
 */
public record AdminDoctorResponseDTO(
        Long id,
        String name,
        String email,
        String specialty,
        String licenseNumber,
        String phoneNumber,
        LocalDateTime registeredAt) {
}
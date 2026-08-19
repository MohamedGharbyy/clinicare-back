package com.clinicare.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Admin-only representation of a registered patient account. Exposes the
 * profile-level contact/demographic fields alongside the linked user's identity.
 */
public record AdminPatientResponseDTO(
        Long id,
        String name,
        String email,
        LocalDate dateOfBirth,
        String phoneNumber,
        LocalDateTime registeredAt) {
}
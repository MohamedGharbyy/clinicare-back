package com.clinicare.dto;

/**
 * Read-only view of the authenticated patient's account and profile, returned
 * by {@code GET /api/patient/profile}. Mirrors the {@link com.clinicare.entity.User}
 * and {@link com.clinicare.entity.PatientProfile} entities without exposing the
 * password hash.
 */
public record UserProfileResponseDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String role,
        String createdAt) {
}

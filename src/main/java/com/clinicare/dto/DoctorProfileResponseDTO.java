package com.clinicare.dto;

/**
 * Read-only view of the authenticated doctor's account and profile, returned by
 * {@code GET /api/doctor/profile}. Mirrors the {@link com.clinicare.entity.User} and
 * {@link com.clinicare.entity.DoctorProfile} entities without exposing the password hash.
 */
public record DoctorProfileResponseDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String specialty,
        String licenseNumber,
        String role,
        String createdAt) {
}

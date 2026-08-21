package com.clinicare.dto;

/**
 * Result of {@code PUT /api/doctor/profile}. Carries the updated profile plus,
 * when the doctor changed their login email, a freshly issued JWT so the client
 * can refresh its session without forcing a re-login. {@code token} is {@code null}
 * when the email did not change.
 */
public record UpdateDoctorProfileResponseDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String specialty,
        String licenseNumber,
        String role,
        String createdAt,
        String token) {
}

package com.clinicare.dto;

/**
 * Result of {@code PUT /api/patient/profile}. Carries the updated profile plus,
 * when the patient changed their login email, a freshly issued JWT so the client
 * can refresh its session without forcing a re-login. {@code token} is {@code null}
 * when the email did not change.
 */
public record UpdatePatientProfileResponseDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String role,
        String createdAt,
        String token) {
}

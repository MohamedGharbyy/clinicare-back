package com.clinicare.dto;

/**
 * Public representation of a doctor used by the booking flow.
 * <p>
 * `id` is the doctor profile's database id ({@code doctor_profiles.id}), the
 * value patients must submit as {@code doctorId} when booking an appointment.
 */
public record DoctorResponseDTO(
        Long id,
        String name,
        String specialty) {
}
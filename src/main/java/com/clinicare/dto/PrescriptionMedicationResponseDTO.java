package com.clinicare.dto;

/**
 * Read-only representation of a single medication within a prescription.
 */
public record PrescriptionMedicationResponseDTO(
        Long id,
        String medicationName,
        String dosage,
        String frequency,
        String duration,
        String instructions) {
}

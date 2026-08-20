package com.clinicare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for a single medication entry inside a prescription request.
 */
public record PrescriptionMedicationRequestDTO(
        @NotBlank(message = "medicationName is required")
        @Size(max = 255, message = "medicationName must not exceed 255 characters")
        String medicationName,

        @NotBlank(message = "dosage is required")
        @Size(max = 100, message = "dosage must not exceed 100 characters")
        String dosage,

        @NotBlank(message = "frequency is required")
        @Size(max = 100, message = "frequency must not exceed 100 characters")
        String frequency,

        @NotBlank(message = "duration is required")
        @Size(max = 100, message = "duration must not exceed 100 characters")
        String duration,

        @NotBlank(message = "instructions is required")
        @Size(max = 1000, message = "instructions must not exceed 1000 characters")
        String instructions) {
}

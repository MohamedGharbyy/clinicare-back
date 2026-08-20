package com.clinicare.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for creating a new prescription.
 * <p>
 * The doctor is always derived from the authenticated user, so this payload
 * intentionally carries no doctor identifier. A prescription must contain
 * at least one medication.
 */
public record PrescriptionRequestDTO(
        @NotNull(message = "patientId is required")
        Long patientId,

        @NotNull(message = "medications is required")
        @Size(min = 1, message = "at least one medication is required")
        List<@Valid PrescriptionMedicationRequestDTO> medications) {
}

package com.clinicare.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only representation of a {@code Prescription} returned by the service
 * layer, including its prescribed medications.
 */
public record PrescriptionResponseDTO(
        Long id,
        Long doctorId,
        String doctorName,
        Long patientId,
        String patientName,
        LocalDateTime creationDate,
        List<PrescriptionMedicationResponseDTO> medications) {
}

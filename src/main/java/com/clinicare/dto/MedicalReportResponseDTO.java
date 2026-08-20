package com.clinicare.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MedicalReportResponseDTO(
        Long id,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        Long appointmentId,
        LocalDate appointmentDate,
        String diagnosis,
        String symptoms,
        String notes,
        LocalDate reportDate,
        LocalDateTime createdAt) {
}

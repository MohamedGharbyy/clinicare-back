package com.clinicare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MedicalReportRequestDTO(
        @NotNull(message = "patientId is required")
        Long patientId,

        Long appointmentId,

        @NotBlank(message = "diagnosis is required")
        @Size(max = 255, message = "diagnosis must not exceed 255 characters")
        String diagnosis,

        @Size(max = 2000, message = "symptoms must not exceed 2000 characters")
        String symptoms,

        @Size(max = 2000, message = "notes must not exceed 2000 characters")
        String notes,

        @NotNull(message = "reportDate is required")
        LocalDate reportDate) {
}

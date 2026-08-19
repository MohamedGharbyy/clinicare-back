package com.clinicare.dto;

/**
 * Aggregated counters backing the admin dashboard's summary cards.
 * Every value is computed from real persisted data (no mock numbers).
 */
public record AdminDashboardResponseDTO(
        long totalPatients,
        long totalDoctors,
        long totalAppointments) {
}
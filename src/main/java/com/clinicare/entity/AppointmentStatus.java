package com.clinicare.entity;

/**
 * Possible lifecycle states for an appointment in the CliniCare system.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED
}
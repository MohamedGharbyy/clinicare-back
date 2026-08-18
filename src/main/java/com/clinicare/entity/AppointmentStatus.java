package com.clinicare.entity;

/**
 * Possible lifecycle states for an appointment in the CliniCare system.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    REJECTED,
    CANCELLED
}
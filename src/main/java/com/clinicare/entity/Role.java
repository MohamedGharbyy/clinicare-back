package com.clinicare.entity;

/**
 * Available user roles in the CliniCare system.
 * <p>ADMIN accounts are not registered through the public endpoint; they are
 * created separately.
 */
public enum Role {
    PATIENT,
    DOCTOR,
    ADMIN
}
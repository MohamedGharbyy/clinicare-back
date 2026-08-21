package com.clinicare.dto;

import com.clinicare.entity.AccountStatus;
import com.clinicare.entity.Role;

import java.time.LocalDateTime;

/**
 * Admin-facing representation of a managed account (PATIENT or DOCTOR).
 * The {@code status} reflects the effective state after any expired ban has
 * been reconciled, and {@code banExpiresAt} is non-null only while a ban is
 * active. For soft-deleted accounts, {@code deletedAt} and {@code deletedBy}
 * (the Admin who deleted it) are populated.
 */
public record AdminUserResponseDTO(
        Long id,
        String name,
        String email,
        Role role,
        AccountStatus status,
        LocalDateTime banExpiresAt,
        LocalDateTime registeredAt,
        String specialty,
        String phoneNumber,
        LocalDateTime deletedAt,
        Long deletedById,
        String deletedByEmail) {
}

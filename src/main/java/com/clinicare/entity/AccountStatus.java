package com.clinicare.entity;

/**
 * Lifecycle state of a {@link User} account as managed by an Admin.
 * <ul>
 *   <li>{@code ACTIVE} – the account can log in and use the platform.</li>
 *   <li>{@code DISABLED} – an Admin turned the account off; the user cannot
 *       log in until an Admin re-enables it.</li>
 *   <li>{@code BANNED} – an Admin temporarily banned the account. When
 *       {@code banExpiresAt} passes, the block is lifted automatically.</li>
 *   <li>{@code DELETED} – a soft delete. The account is hidden from the
 *       active user list and can no longer log in, but its historical records
 *       (appointments, prescriptions, medical reports) are preserved.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    DISABLED,
    BANNED,
    DELETED
}

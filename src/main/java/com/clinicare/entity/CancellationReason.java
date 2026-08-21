package com.clinicare.entity;

/**
 * Enumerates the reasons an appointment was moved to the {@code CANCELLED}
 * state. Currently the only automatic reason is {@code ACCOUNT_BANNED}, which
 * is recorded when an appointment is cancelled because a participating patient
 * or doctor was banned during the scheduled appointment period.
 */
public enum CancellationReason {
    ACCOUNT_BANNED
}

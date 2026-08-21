-- V10__add_cancellation_reason.sql
-- Records why an appointment was cancelled. Used when an appointment is
-- automatically cancelled because a participating patient or doctor was
-- banned during the scheduled appointment period (reason: ACCOUNT_BANNED).
-- Existing cancelled appointments simply have a NULL value for this column.

ALTER TABLE appointments
    ADD COLUMN cancellation_reason VARCHAR(30);

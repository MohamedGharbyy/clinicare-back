-- V9__add_appointment_notification_tracking.sql
-- Tracks the last appointment status for which a notification email was sent.
-- Used to avoid sending duplicate emails when the same status transition is
-- applied more than once (e.g. a transition retried or re-applied).

ALTER TABLE appointments
    ADD COLUMN last_notified_status VARCHAR(20);

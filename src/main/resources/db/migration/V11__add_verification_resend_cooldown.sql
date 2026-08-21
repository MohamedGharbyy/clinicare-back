-- V11__add_verification_resend_cooldown.sql
--
-- Supports the email verification CODE flow (replacing the verification link).
--   verification_email_sent_at : timestamp of the last verification code email
--       sent for this account. Used to enforce the resend cooldown (so a
--       single account cannot be flooded with verification emails) and is
--       nullable because pre-existing / Admin-seeded accounts never need it.

ALTER TABLE users
ADD COLUMN verification_email_sent_at TIMESTAMP;

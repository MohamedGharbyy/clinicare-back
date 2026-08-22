-- V12__allow_deleted_account_emails_to_be_reused.sql
--
-- Enforces email uniqueness only among non-deleted accounts.
--
-- Business rule:
--   * Active/non-deleted account with email -> email unavailable
--   * Deleted account with email -> email available for new registration
--
-- The old global UNIQUE constraint (uk_users_email) prevented ANY duplicate
-- email, including across a deleted account and a new registration. This
-- migration drops that constraint and replaces it with a partial unique
-- index that applies only to rows whose status is not DELETED.

ALTER TABLE users DROP CONSTRAINT uk_users_email;

CREATE UNIQUE INDEX uk_users_email_active
    ON users (email)
    WHERE status != 'DELETED';

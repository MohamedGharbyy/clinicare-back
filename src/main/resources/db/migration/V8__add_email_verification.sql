-- V8__add_email_verification.sql
--
-- Email verification for self-registered PATIENT/DOCTOR accounts.
--   email_verified : whether the account's email address has been confirmed.
--                    Defaults to TRUE so pre-existing and Admin-seeded accounts
--                    (created outside the public registration flow) keep working
--                    without an email confirmation step. Public registration
--                    explicitly sets this to FALSE and issues a verification token.
--   verification_tokens : single-use, expiring tokens used to confirm an email.
--                    Only the SHA-256 hash of the token is stored; the raw token
--                    lives only in the verification link sent by email.

ALTER TABLE users
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE verification_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    purpose     VARCHAR(30)  NOT NULL DEFAULT 'EMAIL_VERIFICATION',
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_verification_tokens_hash UNIQUE (token_hash)
);

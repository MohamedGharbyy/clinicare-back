-- V7__add_account_status.sql
--
-- Account lifecycle state for Admin management of PATIENT/DOCTOR accounts.
--   status         : ACTIVE | DISABLED | BANNED | DELETED  (default ACTIVE)
--   ban_expires_at : when a temporary ban lifts automatically (nullable)
--   deleted_at     : timestamp of a soft delete (nullable)
--   deleted_by     : id of the Admin who performed the soft delete (nullable)

ALTER TABLE users
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE users
ADD COLUMN ban_expires_at TIMESTAMP;

ALTER TABLE users
ADD COLUMN deleted_at TIMESTAMP;

ALTER TABLE users
ADD COLUMN deleted_by BIGINT;

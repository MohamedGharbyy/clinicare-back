-- V3__fix_admin_id_and_shift_users.sql
--
-- Current expected state after V2 + AdminInitializer:
--
--   ID 2 -> Original user 1
--   ID 3 -> Original user 2
--   ID 4 -> Admin
--
-- Desired state:
--
--   ID 1 -> Admin
--   ID 2 -> Original user 1
--   ID 3 -> Original user 2
--
-- Only the admin ID needs to be changed.
-- Foreign-key constraints are temporarily removed so that
-- the user ID can be changed safely.


-- ============================================================
-- Step 1: Remove foreign-key constraints temporarily
-- ============================================================

ALTER TABLE patient_profiles
DROP CONSTRAINT IF EXISTS fk_patient_profiles_user;

ALTER TABLE doctor_profiles
DROP CONSTRAINT IF EXISTS fk_doctor_profiles_user;


-- ============================================================
-- Step 2: Create a temporary column to preserve IDs
-- ============================================================

ALTER TABLE users
ADD COLUMN temp_v3_id BIGINT;


-- ============================================================
-- Step 3: Store current IDs
-- ============================================================

UPDATE users
SET temp_v3_id = id;


-- ============================================================
-- Step 4: Move all users to temporary negative IDs
--
-- This avoids primary-key conflicts.
--
-- Example:
--   2 -> -2
--   3 -> -3
--   4 -> -4
-- ============================================================

UPDATE users
SET id = -temp_v3_id;


-- ============================================================
-- Step 5: Assign final IDs
--
-- Admin:
--   4 -> 1
--
-- Existing users:
--   2 -> 2
--   3 -> 3
--
-- Any additional user:
--   old_id -> old_id
-- ============================================================

UPDATE users
SET id = CASE
    WHEN temp_v3_id = 4 THEN 1
    ELSE temp_v3_id
END;


-- ============================================================
-- Step 6: Update patient profile references
--
-- Only references to the admin need to change:
--   4 -> 1
--
-- All other user references remain unchanged.
-- ============================================================

UPDATE patient_profiles
SET user_id = CASE
    WHEN user_id = 4 THEN 1
    ELSE user_id
END
WHERE user_id IS NOT NULL;


-- ============================================================
-- Step 7: Update doctor profile references
-- ============================================================

UPDATE doctor_profiles
SET user_id = CASE
    WHEN user_id = 4 THEN 1
    ELSE user_id
END
WHERE user_id IS NOT NULL;


-- ============================================================
-- Step 8: Recreate foreign-key constraints
-- ============================================================

ALTER TABLE patient_profiles
ADD CONSTRAINT fk_patient_profiles_user
FOREIGN KEY (user_id)
REFERENCES users(id);

ALTER TABLE doctor_profiles
ADD CONSTRAINT fk_doctor_profiles_user
FOREIGN KEY (user_id)
REFERENCES users(id);


-- ============================================================
-- Step 9: Reset the users sequence
--
-- The highest existing ID should currently be 3.
--
-- Setting the sequence to 3 with is_called = TRUE means:
--
--   next generated ID = 4
--
-- This is exactly what we want for future users.
-- ============================================================

SELECT setval(
    'users_id_seq',
    COALESCE((SELECT MAX(id) FROM users), 1),
    true
);


-- ============================================================
-- Step 10: Remove temporary column
-- ============================================================

ALTER TABLE users
DROP COLUMN temp_v3_id;


-- ============================================================
-- Step 11: Verify the admin
-- ============================================================

DO $$
DECLARE
    v_admin_id BIGINT;
    v_admin_count BIGINT;
BEGIN

    SELECT
        COUNT(*),
        MAX(id)
    INTO
        v_admin_count,
        v_admin_id
    FROM users
    WHERE email = 'admin@clinicare.tn'
      AND role = 'ADMIN';

    IF v_admin_count = 1 AND v_admin_id = 1 THEN

        RAISE NOTICE
            'V3 successful: admin@clinicare.tn is now at ID 1.';

    ELSE

        RAISE EXCEPTION
            'V3 failed: expected exactly one admin at ID 1, found count=% and ID=%',
            v_admin_count,
            v_admin_id;

    END IF;

END $$;
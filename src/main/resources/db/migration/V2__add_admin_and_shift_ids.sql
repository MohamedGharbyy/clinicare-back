-- V2__add_admin_and_shift_ids.sql
--
-- Shift all existing users by +1 to reserve ID 1 for the admin.
--
-- Example:
--
-- Before:
--   users:
--     1 -> User 1
--     2 -> User 2
--
-- After:
--   users:
--     2 -> User 1
--     3 -> User 2
--
-- ID 1 is left available for the admin account.


-- ============================================================
-- Step 1: Add temporary columns
-- ============================================================

ALTER TABLE users
ADD COLUMN temp_old_id BIGINT;

ALTER TABLE patient_profiles
ADD COLUMN temp_old_user_id BIGINT;

ALTER TABLE doctor_profiles
ADD COLUMN temp_old_user_id BIGINT;


-- ============================================================
-- Step 2: Save original IDs
-- ============================================================

UPDATE users
SET temp_old_id = id;


UPDATE patient_profiles
SET temp_old_user_id = user_id;


UPDATE doctor_profiles
SET temp_old_user_id = user_id;


-- ============================================================
-- Step 3: Move foreign-key references to temporary negative IDs
--
-- This must happen BEFORE changing users.id because the FK
-- constraint is still active.
-- ============================================================

UPDATE patient_profiles
SET user_id = -user_id
WHERE user_id IS NOT NULL;


UPDATE doctor_profiles
SET user_id = -user_id
WHERE user_id IS NOT NULL;


-- ============================================================
-- Step 4: Move users to temporary negative IDs
--
-- Example:
--   1 -> -1
--   2 -> -2
-- ============================================================

UPDATE users
SET id = -temp_old_id;


-- ============================================================
-- Step 5: Assign final user IDs
--
-- Example:
--   1 -> 2
--   2 -> 3
-- ============================================================

UPDATE users
SET id = temp_old_id + 1;


-- ============================================================
-- Step 6: Restore patient profile foreign keys
--
-- Example:
--   -1 -> 2
--   -2 -> 3
-- ============================================================

UPDATE patient_profiles
SET user_id = -temp_old_user_id + 1
WHERE temp_old_user_id IS NOT NULL;


-- ============================================================
-- Step 7: Restore doctor profile foreign keys
-- ============================================================

UPDATE doctor_profiles
SET user_id = -temp_old_user_id + 1
WHERE temp_old_user_id IS NOT NULL;


-- ============================================================
-- Step 8: Reset sequence
--
-- FALSE means 1 has not been consumed.
-- Therefore the next generated ID is exactly 1.
-- ============================================================

SELECT setval('users_id_seq', 1, false);


-- ============================================================
-- Step 9: Remove temporary columns
-- ============================================================

ALTER TABLE users
DROP COLUMN temp_old_id;

ALTER TABLE patient_profiles
DROP COLUMN temp_old_user_id;

ALTER TABLE doctor_profiles
DROP COLUMN temp_old_user_id;


-- ============================================================
-- Step 10: Verification
-- ============================================================

DO $$
DECLARE
    v_user_count BIGINT;
    v_min_id BIGINT;
    v_max_id BIGINT;
BEGIN

    SELECT
        COUNT(*),
        MIN(id),
        MAX(id)
    INTO
        v_user_count,
        v_min_id,
        v_max_id
    FROM users;

    RAISE NOTICE 'V2 completed successfully.';
    RAISE NOTICE 'Users: %, ID range: % - %',
        v_user_count,
        v_min_id,
        v_max_id;
    RAISE NOTICE 'ID 1 is reserved for the admin account.';

END $$;
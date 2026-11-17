-- Read-only review by database owner. Not automatically executed.
SELECT table_schema,table_name,column_name,data_type,is_nullable
FROM information_schema.columns
WHERE table_schema='doctor_backend'
ORDER BY table_name,ordinal_position;
SELECT conrelid::regclass AS table_name,conname,pg_get_constraintdef(oid)
FROM pg_constraint
WHERE connamespace='doctor_backend'::regnamespace;
SELECT to_regclass('doctor_backend.wallet_bindings') AS wallet_bindings,
       to_regclass('doctor_backend.wallet_challenges') AS wallet_challenges,
       to_regclass('doctor_backend.record_anchors') AS record_anchors,
       to_regclass('doctor_backend.anchor_attempts') AS anchor_attempts;
-- Counts only: no password/hash/medical content output.
SELECT COUNT(*) AS duplicate_wallet_groups
FROM (SELECT lower(wallet_address) FROM public.users
      WHERE wallet_address IS NOT NULL
      GROUP BY lower(wallet_address) HAVING COUNT(*)>1) duplicates;
SELECT blockchain_status,COUNT(*)
FROM doctor_backend.record_versions GROUP BY blockchain_status;

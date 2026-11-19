-- PROPOSAL ONLY. Group approval + backup required. Do NOT automatically run on Supabase.
-- Additive Doctor-owned metadata. No changes/copies/deletes to public or legacy records.
BEGIN;
CREATE TABLE doctor_backend.shared_version_proofs (
 version_id BIGINT PRIMARY KEY REFERENCES public.record_versions(id),
 canonical_content TEXT NOT NULL, previous_hash VARCHAR(64) NOT NULL,
 test_results TEXT NOT NULL, content_hash VARCHAR(64) NOT NULL
);
CREATE TABLE doctor_backend.shared_uploads (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES public.users(id),
 patient_id BIGINT NOT NULL REFERENCES public.patients(id),
 storage_key VARCHAR(255) UNIQUE NOT NULL,file_name VARCHAR(255) NOT NULL,
 media_type VARCHAR(20) NOT NULL,size_bytes BIGINT NOT NULL,sha256 VARCHAR(64) NOT NULL
);
-- No public policies added: review JDBC role grants/RLS/private-storage ownership with team before deployment.
ALTER TABLE doctor_backend.shared_version_proofs ENABLE ROW LEVEL SECURITY;
ALTER TABLE doctor_backend.shared_uploads ENABLE ROW LEVEL SECURITY;
COMMIT;
-- Rollback: disable clinical write endpoints/revert code first; retain these tables and private files as evidence.
-- Do not DROP once real records exist. Backup/export sidecar metadata before any reviewed rollback.

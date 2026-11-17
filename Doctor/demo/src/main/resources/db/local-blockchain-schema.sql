-- ISOLATED LOCAL/TEST INITIALIZATION ONLY
-- REVIEW REQUIRED. Never executed automatically on Supabase.
-- Additive Doctor-owned tables; public schema and immutable record_versions remain unchanged.
CREATE TABLE doctor_backend.wallet_bindings (
 user_id BIGINT PRIMARY KEY REFERENCES public.users(id),
 address VARCHAR(42) NOT NULL UNIQUE CHECK (address=lower(address)),
 chain_id BIGINT NOT NULL CHECK (chain_id=11155111),
 verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
 proof_method VARCHAR(255) NOT NULL CHECK (proof_method='PERSONAL_SIGN')
);
CREATE TABLE doctor_backend.wallet_challenges (
 id VARCHAR(255) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES public.users(id),
 doctor_id BIGINT NOT NULL REFERENCES public.doctors(id), address VARCHAR(42) NOT NULL,
 message VARCHAR(2048) NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX wallet_challenge_owner ON doctor_backend.wallet_challenges(user_id,expires_at);
CREATE TABLE doctor_backend.record_anchors (
 record_id BIGINT PRIMARY KEY REFERENCES doctor_backend.medical_records(id),
 record_key VARCHAR(66) NOT NULL UNIQUE,
 patient_wallet VARCHAR(42) NOT NULL
);
CREATE TABLE doctor_backend.anchor_attempts (
 id VARCHAR(255) PRIMARY KEY,
 record_id BIGINT NOT NULL REFERENCES doctor_backend.medical_records(id),
 version_id BIGINT NOT NULL REFERENCES doctor_backend.record_versions(id),
 version_number INTEGER NOT NULL,
 user_id BIGINT NOT NULL REFERENCES public.users(id),
 doctor_id BIGINT NOT NULL REFERENCES public.doctors(id),
 record_key VARCHAR(66) NOT NULL,
 patient_wallet VARCHAR(42) NOT NULL, doctor_wallet VARCHAR(42) NOT NULL,
 content_hash VARCHAR(66) NOT NULL, previous_hash VARCHAR(66) NOT NULL,
 transaction_hash VARCHAR(66) UNIQUE,
 status VARCHAR(255) NOT NULL CHECK (status IN ('PREPARED','PENDING','CONFIRMED','FAILED')),
 code VARCHAR(255) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
 scan_from BIGINT NOT NULL, poll_count INTEGER NOT NULL,
 block_number BIGINT, block_hash VARCHAR(66)
);
CREATE INDEX anchor_version_lookup ON doctor_backend.anchor_attempts(record_id,version_number,created_at);
CREATE INDEX anchor_due_lookup ON doctor_backend.anchor_attempts(status,checked_at);
-- Retain all attempts. No public DDL and no rewrite of existing content hashes.
-- record_versions.blockchain_status CHECK NOT_SUBMITTED intentionally retained: legacy snapshot metadata.
-- Effective status/tx lives in anchor_attempts and is returned by anchor-status endpoint.

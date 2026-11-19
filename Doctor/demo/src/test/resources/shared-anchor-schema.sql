CREATE TABLE doctor_backend.shared_record_anchors (
 record_id BIGINT PRIMARY KEY REFERENCES public.medical_records(id),
 record_key VARCHAR(66) UNIQUE NOT NULL,
 patient_wallet VARCHAR(42) NOT NULL
);
CREATE TABLE doctor_backend.shared_anchor_intents (
 version_id BIGINT PRIMARY KEY REFERENCES public.record_versions(id),
 doctor_wallet VARCHAR(42) NOT NULL,
 content_hash VARCHAR(66) NOT NULL,
 previous_hash VARCHAR(66) NOT NULL,
 scan_from BIGINT NOT NULL,
 transaction_hash VARCHAR(66) UNIQUE,
 status VARCHAR(16) NOT NULL CHECK (status IN ('PREPARED','PENDING','CONFIRMED','FAILED')),
 code VARCHAR(80) NOT NULL,
 block_number BIGINT,
 block_hash VARCHAR(66),
 prepared_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

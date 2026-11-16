-- PROPOSAL ONLY. Not an automatic migration. Requires Patient/Admin team review.
-- Prerequisite: public.users, public.doctors, public.specialties and verified shared constraints.
-- patient_links.id is a Doctor-module bridge ID, NOT the teammate's patients.id.
CREATE SCHEMA IF NOT EXISTS doctor_backend;
CREATE TABLE doctor_backend.patient_links (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 user_id BIGINT NOT NULL UNIQUE REFERENCES public.users(id)
);
CREATE TABLE doctor_backend.access_grants (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 patient_id BIGINT NOT NULL REFERENCES doctor_backend.patient_links(id),
 doctor_id BIGINT NOT NULL REFERENCES public.doctors(id),
 can_read BOOLEAN NOT NULL, can_create BOOLEAN NOT NULL, can_update BOOLEAN NOT NULL,
 valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE, revoked_at TIMESTAMP WITH TIME ZONE,
 CHECK (expires_at IS NULL OR expires_at > valid_from),
 CHECK ((NOT can_create AND NOT can_update) OR can_read)
);
CREATE INDEX grant_lookup ON doctor_backend.access_grants(doctor_id,patient_id,revoked_at);
CREATE TABLE doctor_backend.medical_records (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 patient_id BIGINT NOT NULL REFERENCES doctor_backend.patient_links(id),
 created_by BIGINT NOT NULL REFERENCES public.doctors(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 current_version INTEGER NOT NULL CHECK (current_version > 0), lock_version BIGINT
);
CREATE INDEX record_patient ON doctor_backend.medical_records(patient_id,id);
CREATE INDEX record_creator ON doctor_backend.medical_records(created_by,id);
CREATE TABLE doctor_backend.attachments (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 patient_id BIGINT NOT NULL REFERENCES doctor_backend.patient_links(id),
 uploaded_by BIGINT NOT NULL REFERENCES public.doctors(id),
 storage_key VARCHAR(255) NOT NULL UNIQUE, media_type VARCHAR(255) NOT NULL,
 size_bytes BIGINT NOT NULL CHECK (size_bytes > 0), sha256 VARCHAR(64) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE doctor_backend.record_versions (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 record_id BIGINT NOT NULL REFERENCES doctor_backend.medical_records(id),
 version_number INTEGER NOT NULL CHECK (version_number > 0),
 doctor_id BIGINT NOT NULL REFERENCES public.doctors(id),
 previous_version_id BIGINT REFERENCES doctor_backend.record_versions(id), previous_hash VARCHAR(64),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, exam_date DATE NOT NULL,
 diagnosis VARCHAR(10000) NOT NULL, prescription VARCHAR(10000) NOT NULL,
 test_results VARCHAR(10000) NOT NULL, notes VARCHAR(10000) NOT NULL,
 canonical_content VARCHAR(100000) NOT NULL, content_hash VARCHAR(64) NOT NULL,
 canonical_schema VARCHAR(255) NOT NULL,
 blockchain_status VARCHAR(255) NOT NULL CHECK (blockchain_status = 'NOT_SUBMITTED'),
 blockchain_transaction_hash VARCHAR(255), blockchain_record_id VARCHAR(255),
 UNIQUE(record_id,version_number),
 CHECK ((version_number=1 AND previous_version_id IS NULL AND previous_hash IS NULL)
     OR (version_number>1 AND previous_version_id IS NOT NULL AND previous_hash IS NOT NULL))
);
CREATE TABLE doctor_backend.version_attachments (
 version_id BIGINT NOT NULL REFERENCES doctor_backend.record_versions(id),
 position INTEGER NOT NULL, attachment_id BIGINT NOT NULL REFERENCES doctor_backend.attachments(id),
 PRIMARY KEY(version_id,position), UNIQUE(version_id,attachment_id)
);
CREATE TABLE doctor_backend.audit_logs (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 actor_user_id BIGINT NOT NULL REFERENCES public.users(id),
 action VARCHAR(32) NOT NULL, resource VARCHAR(200) NOT NULL,
 outcome VARCHAR(32) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX audit_actor_time ON doctor_backend.audit_logs(actor_user_id,occurred_at);

-- Isolated H2 fixture aligned with column/FK screenshots supplied on 18/11/2026.
-- Not a Supabase migration. VARCHAR lengths and truncated Patient CHECK expressions are unverified.
CREATE TABLE public.patients (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 user_id BIGINT NOT NULL UNIQUE REFERENCES public.users(id),
 patient_code VARCHAR NOT NULL UNIQUE,
 date_of_birth DATE NOT NULL,
 gender VARCHAR NOT NULL,
 blood_type VARCHAR,
 phone VARCHAR,
 address VARCHAR,
 follow_status VARCHAR NOT NULL DEFAULT 'FOLLOWING'
);
CREATE TABLE public.access_grants (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 patient_id BIGINT NOT NULL REFERENCES public.patients(id),
 doctor_id BIGINT NOT NULL CONSTRAINT public_grants_doctor_fk REFERENCES public.doctors(id),
 status VARCHAR NOT NULL DEFAULT 'PENDING',
 revoked BOOLEAN NOT NULL DEFAULT false,
 expires_at TIMESTAMP WITHOUT TIME ZONE,
 tx_hash VARCHAR,
 created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 responded_at TIMESTAMP WITHOUT TIME ZONE
);

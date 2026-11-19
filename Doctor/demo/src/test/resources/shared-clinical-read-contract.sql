-- H2 SELECT-reader fixture from pg_catalog metadata inspected 18/11/2026.
-- Never referenced by production or applied to Supabase.
CREATE TABLE public.medical_records (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,record_code VARCHAR(30) NOT NULL UNIQUE,
 patient_id BIGINT NOT NULL REFERENCES public.patients(id),doctor_id BIGINT NOT NULL REFERENCES public.doctors(id),
 specialty_id BIGINT NOT NULL REFERENCES public.specialties(id),visit_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
 reason VARCHAR(500) NOT NULL,status VARCHAR(20) NOT NULL,current_version INTEGER NOT NULL,created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
CREATE TABLE public.record_versions (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,record_id BIGINT NOT NULL REFERENCES public.medical_records(id),
 version_no INTEGER NOT NULL,diagnosis TEXT,prescription TEXT,note TEXT,data_hash VARCHAR(66),tx_hash VARCHAR(66),
 chain_status VARCHAR(10) NOT NULL,created_by BIGINT NOT NULL REFERENCES public.users(id),created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
 room_name VARCHAR(50),visit_type VARCHAR(100),secondary_diagnosis TEXT,treatment_direction VARCHAR(255),
 next_visit_date DATE,next_visit_note TEXT,UNIQUE(record_id,version_no)
);
CREATE TABLE public.prescription_items (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,record_version_id BIGINT NOT NULL REFERENCES public.record_versions(id) ON DELETE CASCADE,
 sort_order INTEGER NOT NULL,medicine_name VARCHAR(150) NOT NULL,active_ingredient VARCHAR(150),dosage_instruction VARCHAR(255),quantity_text VARCHAR(50) NOT NULL
);
CREATE TABLE public.vital_signs (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,record_version_id BIGINT NOT NULL UNIQUE REFERENCES public.record_versions(id) ON DELETE CASCADE,
 blood_pressure VARCHAR(20),heart_rate INTEGER,temperature NUMERIC(4,1),spo2 INTEGER,weight NUMERIC(5,2),height NUMERIC(5,1),bmi NUMERIC(4,1)
);
CREATE TABLE public.record_attachments (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,record_version_id BIGINT NOT NULL REFERENCES public.record_versions(id) ON DELETE CASCADE,
 file_name VARCHAR(255) NOT NULL,file_type VARCHAR(20) NOT NULL,file_size_label VARCHAR(30),file_url TEXT NOT NULL,uploaded_at TIMESTAMP WITHOUT TIME ZONE
);

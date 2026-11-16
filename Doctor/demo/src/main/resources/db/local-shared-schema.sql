-- ONLY for isolated H2 profile. Never execute against Supabase.
CREATE TABLE public.users (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 email VARCHAR(255) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL,
 full_name VARCHAR(255) NOT NULL, role VARCHAR(255) NOT NULL,
 wallet_address VARCHAR(255), enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE public.specialties (id BIGINT PRIMARY KEY,code VARCHAR(255),name VARCHAR(255));
CREATE TABLE public.doctors (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 user_id BIGINT NOT NULL UNIQUE REFERENCES public.users(id),
 specialty_id BIGINT NOT NULL REFERENCES public.specialties(id),
 full_name VARCHAR(255) NOT NULL,email VARCHAR(255) NOT NULL,
 password VARCHAR(255) NOT NULL,license_number VARCHAR(255) NOT NULL,
 license_no VARCHAR(255),hospital VARCHAR(255) NOT NULL,
 status VARCHAR(255) NOT NULL,approval_status VARCHAR(255) NOT NULL,
 approved_by BIGINT REFERENCES public.users(id),approved_at TIMESTAMP
);

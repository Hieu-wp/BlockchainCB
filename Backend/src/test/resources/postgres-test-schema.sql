-- Chỉ fixture của database tạm do test khởi tạo (PostgreSQL/H2). KHÔNG chạy trên Supabase.
CREATE TABLE public.users (
    id BIGSERIAL PRIMARY KEY, email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL, full_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL, wallet_address VARCHAR(255),
    enabled BOOLEAN NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE public.doctors (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES public.users(id),
    specialty_id BIGINT NOT NULL, license_no VARCHAR(255),
    approval_status VARCHAR(255) NOT NULL CHECK (approval_status IN ('PENDING','APPROVED','REJECTED')),
    approved_by BIGINT REFERENCES public.users(id), approved_at TIMESTAMP,
    email VARCHAR(255) NOT NULL, full_name VARCHAR(255) NOT NULL,
    hospital VARCHAR(255) NOT NULL, license_number VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL, status VARCHAR(255) NOT NULL
);
CREATE TABLE public.admin_audit_logs (
    id BIGSERIAL PRIMARY KEY, admin_id BIGINT NOT NULL REFERENCES public.users(id),
    action VARCHAR(64) NOT NULL CHECK (action IN ('USER_LOCK','USER_UNLOCK','DOCTOR_REJECT','DOCTOR_APPROVE','DOCTOR_SUSPEND','DOCTOR_REACTIVATE')),
    target_type VARCHAR(64) NOT NULL, target_id BIGINT NOT NULL,
    description VARCHAR(500) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

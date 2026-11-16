package com.example.medical.entity;

/**
 * Cac enum dung chung. Gia tri phai trung voi CHECK constraint trong schema_supabase.sql.
 * (Java chi cho 1 public top-level type / file, nen gom vao class long nhau.)
 */
public final class Enums {
    private Enums() {}

    public enum Role { PATIENT, DOCTOR, ADMIN }

    public enum Gender { MALE, FEMALE, OTHER }

    public enum FollowStatus { FOLLOWING, STOPPED }

    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }

    public enum RecordStatus { COMPLETED, PENDING_RESULT, CANCELLED }

    public enum ChainStatus { NONE, PENDING, CONFIRMED, FAILED }
}

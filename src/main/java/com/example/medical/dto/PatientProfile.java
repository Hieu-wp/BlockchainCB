package com.example.medical.dto;

import java.time.LocalDate;

/** Du lieu cho phan header thong tin benh nhan. Tuoi duoc tinh tu ngay sinh, khong luu DB. */
public record PatientProfile(
        String fullName,
        String patientCode,
        LocalDate dateOfBirth,
        int age,
        String gender,
        String bloodType,
        String phone,
        String address,
        String followStatus,
        String walletAddress) {
}
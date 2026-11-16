package com.example.medical.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RecordDetail(
        Long id,
        String recordCode,
        LocalDateTime visitAt,
        String specialtyName,
        String doctorName,
        String reason,
        String status,
        int version,
        // Thông tin hành chính bổ sung
        String patientFullName,
        String patientCode,
        LocalDate patientDob,
        int patientAge,
        String patientGender,
        String patientBloodType,
        String roomName,
        String visitType,
        // Lâm sàng & Chẩn đoán
        String diagnosis,
        String secondaryDiagnosis,
        String treatmentDirection,
        String prescriptionNote,
        String note,
        LocalDate nextVisitDate,
        String nextVisitNote,
        // Dữ liệu con
        VitalDto vitals,
        List<PrescriptionItemDto> prescriptions,
        List<AttachmentDto> attachments,
        // Blockchain
        String dataHash,
        String chainStatus
) {
    public record VitalDto(
            String bloodPressure, Integer heartRate, BigDecimal temperature,
            Integer spo2, BigDecimal weight, BigDecimal height, BigDecimal bmi
    ) {}

    public record PrescriptionItemDto(
            int sortOrder, String medicineName, String activeIngredient,
            String dosageInstruction, String quantityText
    ) {}

    public record AttachmentDto(
            Long id, String fileName, String fileType, String fileSizeLabel, String fileUrl
    ) {}
}
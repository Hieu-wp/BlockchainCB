package com.blockchaincb.demo.dto;

import java.math.BigDecimal;
import java.util.List;

/** Separate public DTOs: no fake canonical v1 or verified Blockchain result. */
public final class SharedRecordResponse {
  private SharedRecordResponse() {}

  public record Summary(
      long id,
      String recordCode,
      long patientId,
      String patientCode,
      String patientName,
      long doctorId,
      String specialty,
      String visitAt,
      String reason,
      String status,
      int currentVersion,
      String createdAt) {
    @Override
    public String toString() {
      return "SharedRecordSummary[REDACTED]";
    }
  }

  public record VersionSummary(
      long id, int versionNumber, long createdByUserId, String createdAt) {}

  public record Medicine(
      int sortOrder,
      String medicineName,
      String activeIngredient,
      String dosageInstruction,
      String quantityText) {}

  public record Vitals(
      String bloodPressure,
      Integer heartRate,
      BigDecimal temperature,
      Integer spo2,
      BigDecimal weight,
      BigDecimal height,
      BigDecimal bmi) {}

  public record File(
      long id, String fileName, String fileType, String sizeLabel, String uploadedAt) {}

  public record Version(
      long id,
      int versionNumber,
      long createdByUserId,
      String createdAt,
      String diagnosis,
      String prescription,
      String note,
      String roomName,
      String visitType,
      String secondaryDiagnosis,
      String treatmentDirection,
      String nextVisitDate,
      String nextVisitNote,
      String storedDataHash,
      String storedTransactionHash,
      String storedChainStatus,
      String hashVerification,
      List<Medicine> medicines,
      Vitals vitals,
      List<File> attachments,
      boolean downloadAvailable) {
    @Override
    public String toString() {
      return "SharedRecordVersion[REDACTED]";
    }
  }

  public record Detail(Summary record, Version version) {
    @Override
    public String toString() {
      return "SharedRecordDetail[REDACTED]";
    }
  }
}

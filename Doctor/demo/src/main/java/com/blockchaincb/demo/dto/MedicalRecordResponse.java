package com.blockchaincb.demo.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MedicalRecordResponse(
    Long id,
    Long patientId,
    Long createdBy,
    Instant createdAt,
    Instant updatedAt,
    int currentVersion,
    VersionResponse version) {
  @Override
  public String toString() {
    return "MedicalRecordResponse[REDACTED]";
  }

  public record VersionResponse(
      Long id,
      int versionNumber,
      Long doctorId,
      Instant createdAt,
      Long previousVersionId,
      String previousHash,
      LocalDate examDate,
      String diagnosis,
      String prescription,
      String testResults,
      String notes,
      List<Long> attachmentIds,
      String contentHash,
      String canonicalSchema,
      String blockchainStatus,
      String blockchainTransactionHash,
      String blockchainRecordId) {
    @Override
    public String toString() {
      return "VersionResponse[REDACTED]";
    }
  }
}

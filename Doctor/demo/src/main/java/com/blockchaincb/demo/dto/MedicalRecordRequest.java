package com.blockchaincb.demo.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record MedicalRecordRequest(
    @NotNull @Positive Long patientId,
    @NotNull @PastOrPresent LocalDate examDate,
    @NotBlank @Size(max = 10000) String diagnosis,
    @NotNull @Size(max = 10000) String prescription,
    @NotNull @Size(max = 10000) String testResults,
    @NotNull @Size(max = 10000) String notes,
    @NotNull @Size(max = 10) List<@NotNull @Positive Long> attachmentIds) {
  @Override
  public String toString() {
    return "MedicalRecordRequest[REDACTED]";
  }
}

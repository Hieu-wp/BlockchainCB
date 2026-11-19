package com.blockchaincb.demo.shared;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;

public record ClinicalRequest(
    @NotNull @Positive Long patientId, @NotNull @PastOrPresent LocalDateTime visitAt,
    @NotBlank @Size(max=500) String reason,
    @NotBlank @Size(max=10000) String diagnosis,
    @Size(max=10000) String secondaryDiagnosis, @Size(max=10000) String testResults,
    @Size(max=255) String treatmentDirection, @Size(max=10000) String note,
    @Size(max=100) String visitType, @Size(max=50) String roomName,
    @FutureOrPresent LocalDate nextVisitDate, @Size(max=10000) String nextVisitNote,
    @NotNull @Size(max=100) List<@NotNull @Valid Medicine> medicines,
    @Valid Vitals vitals, @NotNull @Size(max=10) List<@NotNull @Positive Long> uploadIds,
    @NotNull @Size(max=10) List<@NotNull @Positive Long> keepAttachmentIds) {
  public record Medicine(@NotBlank @Size(max=150) String medicineName,
      @Size(max=150) String activeIngredient,@Size(max=255) String dosageInstruction,
      @NotBlank @Size(max=50) String quantityText) {}
  public record Vitals(@Size(max=20) String bloodPressure,
      @Min(1) @Max(300) Integer heartRate,
      @DecimalMin("25") @DecimalMax("45") BigDecimal temperature,
      @Min(0) @Max(100) Integer spo2,
      @DecimalMin("0.01") @DecimalMax("999.99") BigDecimal weight,
      @DecimalMin("0.1") @DecimalMax("300") BigDecimal height,
      @DecimalMin("0.1") @DecimalMax("100") BigDecimal bmi) {}
  public record Update(@Positive int expectedVersion,@NotNull @Valid ClinicalRequest content) {}
  @Override public String toString(){return "ClinicalRequest[REDACTED]";}
}

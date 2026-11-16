package com.blockchaincb.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateRecordRequest(
    @Positive int expectedVersion, @NotNull @Valid MedicalRecordRequest content) {
  @Override
  public String toString() {
    return "UpdateRecordRequest[REDACTED]";
  }
}

package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.dto.SharedRecordResponse.*;
import com.blockchaincb.demo.security.DoctorPrincipal;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/doctor")
public class SharedRecordController {
  private final SharedRecordService service;

  public SharedRecordController(SharedRecordService service) {
    this.service = service;
  }

  @GetMapping("/shared-records")
  public Page<Summary> history(
      @AuthenticationPrincipal DoctorPrincipal actor,
      @RequestParam(defaultValue = "") @Size(max = 128) String patientCode,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
    return service.history(actor, patientCode, null, PageRequest.of(page, size));
  }

  @GetMapping("/shared-patients/{patient}/records")
  public Page<Summary> patient(
      @AuthenticationPrincipal DoctorPrincipal actor,
      @PathVariable @Positive Long patient,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
    return service.history(actor, "", patient, PageRequest.of(page, size));
  }

  @GetMapping("/shared-records/{id}")
  public Detail detail(
      @AuthenticationPrincipal DoctorPrincipal actor, @PathVariable @Positive Long id) {
    return service.detail(actor, id);
  }

  @GetMapping("/shared-records/{id}/versions")
  public Page<VersionSummary> versions(
      @AuthenticationPrincipal DoctorPrincipal actor,
      @PathVariable @Positive Long id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
    return service.versions(actor, id, PageRequest.of(page, size));
  }

  @GetMapping("/shared-records/{id}/versions/{number}")
  public Version version(
      @AuthenticationPrincipal DoctorPrincipal actor,
      @PathVariable @Positive Long id,
      @PathVariable @Positive int number) {
    return service.version(actor, id, number);
  }
}

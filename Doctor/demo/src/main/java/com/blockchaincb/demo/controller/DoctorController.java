package com.blockchaincb.demo.controller;

import com.blockchaincb.demo.dto.*;
import com.blockchaincb.demo.security.DoctorPrincipal;
import com.blockchaincb.demo.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/doctor")
public class DoctorController {
  private final DoctorService doctors;
  private final PatientService patients;
  private final MedicalRecordService records;
  private final AttachmentService attachments;

  public DoctorController(
      DoctorService doctors,
      PatientService patients,
      MedicalRecordService records,
      AttachmentService attachments) {
    this.doctors = doctors;
    this.patients = patients;
    this.records = records;
    this.attachments = attachments;
  }

  private Pageable page(int page, int size) {
    return PageRequest.of(page, size);
  }

  @GetMapping("/me")
  public DoctorResponse me(@AuthenticationPrincipal DoctorPrincipal p) {
    return doctors.me(p);
  }

  @PatchMapping("/me")
  public DoctorResponse update(
      @AuthenticationPrincipal DoctorPrincipal p, @Valid @RequestBody ProfileUpdateRequest req) {
    return doctors.update(p, req);
  }

  @GetMapping("/patients")
  public Page<PatientService.PatientResponse> patients(
      @AuthenticationPrincipal DoctorPrincipal p,
      @RequestParam(defaultValue = "") @Size(max = 100) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return patients.list(p, q, page(page, size));
  }

  @GetMapping("/patients/{patientId}/records")
  public Page<MedicalRecordResponse> patientRecords(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long patientId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return records.patientRecords(p, patientId, page(page, size));
  }

  @PostMapping("/records")
  @ResponseStatus(HttpStatus.CREATED)
  public MedicalRecordResponse create(
      @AuthenticationPrincipal DoctorPrincipal p, @Valid @RequestBody MedicalRecordRequest req) {
    return records.create(p, req);
  }

  @GetMapping("/records")
  public Page<MedicalRecordResponse> history(
      @AuthenticationPrincipal DoctorPrincipal p,
      @RequestParam(required = false) @Positive Long patientId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return records.history(p, patientId, page(page, size));
  }

  @GetMapping("/records/{id}")
  public MedicalRecordResponse get(
      @AuthenticationPrincipal DoctorPrincipal p, @PathVariable @Positive Long id) {
    return records.get(p, id);
  }

  @PostMapping("/records/{id}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  public MedicalRecordResponse append(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long id,
      @Valid @RequestBody UpdateRecordRequest req) {
    return records.update(p, id, req);
  }

  @GetMapping("/records/{id}/versions")
  public Page<MedicalRecordResponse.VersionResponse> versions(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return records.versions(p, id, page(page, size));
  }

  @GetMapping("/records/{id}/versions/{number}")
  public MedicalRecordResponse.VersionResponse version(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long id,
      @PathVariable @Positive int number) {
    return records.getVersion(p, id, number);
  }

  @GetMapping("/records/{id}/versions/{number}/hash")
  public MedicalRecordService.HashResponse hash(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long id,
      @PathVariable @Positive int number) {
    return records.hash(p, id, number);
  }

  @PostMapping(
      value = "/patients/{patientId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AttachmentService.UploadResponse upload(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long patientId,
      @RequestPart("file") MultipartFile file)
      throws IOException {
    return attachments.upload(p, patientId, file.getBytes());
  }

  @GetMapping("/records/{id}/versions/{number}/attachments/{attachmentId}")
  public ResponseEntity<byte[]> download(
      @AuthenticationPrincipal DoctorPrincipal p,
      @PathVariable @Positive Long id,
      @PathVariable @Positive int number,
      @PathVariable @Positive Long attachmentId) {
    var file = records.download(p, id, number, attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.mediaType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=medical-attachment")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(file.content());
  }
}

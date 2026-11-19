package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.security.DoctorPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/doctor")
public class SharedGrantController {
  private final SharedGrantService service;
  private final SharedGrantRepository repository;
  private final SharedIntegrationSettings settings;
  private final SharedRecordRepository records;

  public SharedGrantController(
      SharedGrantService service,
      SharedGrantRepository repository,
      SharedIntegrationSettings settings,
      SharedRecordRepository records) {
    this.service = service;
    this.repository = repository;
    this.settings = settings;
    this.records = records;
  }

  public record Request(@NotBlank @Size(max = 128) String patientCode) {}

  public record Config(
      boolean sharedGrantsEnabled,
      boolean schemaReady,
      boolean sharedRecordsReady,
      String activeScope,
      boolean sharedHistoryReady) {}

  @GetMapping("/integration")
  public Config config() {
    if (!settings.enabled) return new Config(false, false, false, "UNAGREED", false);
    repository.contract();
    return new Config(true, true, false, "READ_ONLY_PENDING_TEAM_DECISION", records.available());
  }

  @PostMapping("/access-grants/request")
  public SharedGrantService.Requested request(
      @AuthenticationPrincipal DoctorPrincipal actor, @Valid @RequestBody Request request) {
    return service.request(actor, request.patientCode());
  }

  @GetMapping("/access-grants")
  public Page<SharedGrantRepository.Grant> list(
      @AuthenticationPrincipal DoctorPrincipal actor,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
    return service.requests(actor, PageRequest.of(page, size));
  }
}

package com.blockchaincb.demo.service;

import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.*;
import com.blockchaincb.demo.security.*;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {
  private final PatientRepository patients;
  private final UserRepository users;
  private final DoctorIdentityService identities;
  private final com.blockchaincb.demo.shared.SharedIntegrationSettings integration;
  private final com.blockchaincb.demo.shared.SharedGrantService shared;

  public PatientService(
      PatientRepository patients,
      UserRepository users,
      DoctorIdentityService identities,
      com.blockchaincb.demo.shared.SharedIntegrationSettings integration,
      com.blockchaincb.demo.shared.SharedGrantService shared) {
    this.patients = patients;
    this.users = users;
    this.identities = identities;
    this.integration = integration;
    this.shared = shared;
  }

  public record PatientResponse(Long id, String fullName, String patientCode) {
    public PatientResponse(Long id, String fullName) {
      this(id, fullName, null);
    }
  }

  @Transactional(readOnly = true)
  public Page<PatientResponse> list(DoctorPrincipal actor, String query, Pageable page) {
    identities.approved(actor);
    if (integration.enabled)
      return shared
          .patients(actor, query, page)
          .map(p -> new PatientResponse(p.id(), p.fullName(), p.patientCode()));
    return patients
        .visible(actor.doctorId(), query, Instant.now(), page)
        .map(
            p ->
                new PatientResponse(
                    p.getId(),
                    users
                        .findById(p.getUserId())
                        .orElseThrow(() -> new ApiException(409, "Lien ket Patient khong hop le"))
                        .getFullName()));
  }
}

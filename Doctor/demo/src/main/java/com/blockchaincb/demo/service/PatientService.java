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

  public PatientService(
      PatientRepository patients, UserRepository users, DoctorIdentityService identities) {
    this.patients = patients;
    this.users = users;
    this.identities = identities;
  }

  public record PatientResponse(Long id, String fullName) {}

  @Transactional(readOnly = true)
  public Page<PatientResponse> list(DoctorPrincipal actor, String query, Pageable page) {
    identities.approved(actor);
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

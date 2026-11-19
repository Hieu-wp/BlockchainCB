package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedGrantService {
  private final SharedGrantRepository grants;
  private final SharedIntegrationSettings settings;
  private final DoctorIdentityService identities;

  public SharedGrantService(
      SharedGrantRepository grants,
      SharedIntegrationSettings settings,
      DoctorIdentityService identities) {
    this.grants = grants;
    this.settings = settings;
    this.identities = identities;
  }

  public record Requested(long id, String patientCode, String status, boolean existing) {}

  private long doctor(DoctorPrincipal actor) {
    identities.approved(actor);
    settings.requireEnabled();
    return grants.contract().doctorReferencesUser() ? actor.userId() : actor.doctorId();
  }

  @Transactional
  public Requested request(DoctorPrincipal actor, String patientCode) {
    long doctor = doctor(actor);
    String code = patientCode.trim();
    long patient =
        grants
            .lockPatient(code)
            .orElseThrow(() -> new ApiException(404, "PATIENT_CODE_UNAVAILABLE"));
    if (!grants.patientAccountValid(patient))
      throw new ApiException(404, "PATIENT_CODE_UNAVAILABLE");
    var existing = grants.open(patient, doctor);
    if (existing.size() > 1) throw new ApiException(409, "DUPLICATE_GRANTS_REVIEW_REQUIRED");
    if (!existing.isEmpty()) {
      var g = existing.get(0);
      return new Requested(g.id(), code, g.status(), true);
    }
    try {
      return new Requested(grants.insertPending(patient, doctor), code, "PENDING", false);
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(409, "ACCESS_REQUEST_SCHEMA_CONFLICT");
    }
  }

  @Transactional(readOnly = true)
  public Page<SharedGrantRepository.Grant> requests(DoctorPrincipal actor, Pageable page) {
    return grants.requests(doctor(actor), page);
  }

  @Transactional(readOnly = true)
  public Page<SharedGrantRepository.VisiblePatient> patients(
      DoctorPrincipal actor, String q, Pageable page) {
    return grants.visible(doctor(actor), q, page);
  }
}

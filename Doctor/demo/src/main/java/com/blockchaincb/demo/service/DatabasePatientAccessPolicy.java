package com.blockchaincb.demo.service;

import com.blockchaincb.demo.entity.Patient;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabasePatientAccessPolicy implements PatientAccessPolicy {
  private final AccessGrantRepository grants;
  private final PatientRepository patients;

  public DatabasePatientAccessPolicy(AccessGrantRepository grants, PatientRepository patients) {
    this.grants = grants;
    this.patients = patients;
  }

  // Grant rows stay locked until the surrounding medical operation commits.
  @Transactional
  public Patient require(Long doctorId, Long patientId, Action action) {
    boolean allowed =
        grants.activeLocked(patientId, doctorId, Instant.now()).stream()
            .anyMatch(
                g ->
                    switch (action) {
                      case READ -> g.getCanRead();
                      case CREATE -> g.getCanCreate();
                      case UPDATE -> g.getCanUpdate();
                    });
    if (!allowed) throw new ApiException(403, "Khong co quyen hien hanh voi benh nhan");
    return patients
        .findById(patientId)
        .orElseThrow(() -> new ApiException(403, "Khong co quyen hien hanh voi benh nhan"));
  }
}

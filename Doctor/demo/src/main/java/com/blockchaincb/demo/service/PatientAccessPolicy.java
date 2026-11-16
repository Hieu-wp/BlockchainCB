package com.blockchaincb.demo.service;

import com.blockchaincb.demo.entity.Patient;

public interface PatientAccessPolicy {
  enum Action {
    READ,
    CREATE,
    UPDATE
  }

  Patient require(Long doctorId, Long patientId, Action action);
}

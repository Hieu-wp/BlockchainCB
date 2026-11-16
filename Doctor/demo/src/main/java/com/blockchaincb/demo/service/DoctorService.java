package com.blockchaincb.demo.service;

import com.blockchaincb.demo.dto.*;
import com.blockchaincb.demo.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {
  private final DoctorIdentityService identities;

  public DoctorService(DoctorIdentityService identities) {
    this.identities = identities;
  }

  @Transactional(readOnly = true)
  public DoctorResponse me(DoctorPrincipal actor) {
    return DoctorResponse.from(identities.load(actor.userId()));
  }

  @Transactional
  public DoctorResponse update(DoctorPrincipal actor, ProfileUpdateRequest request) {
    var d = identities.load(actor.userId());
    d.getUser().setFullName(request.fullName().trim());
    d.setFullName(request.fullName().trim());
    d.setHospital(request.hospital().trim());
    return DoctorResponse.from(d);
  }
}

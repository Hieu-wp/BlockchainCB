package com.blockchaincb.demo.security;

import com.blockchaincb.demo.entity.Doctor;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorIdentityService {
  private final DoctorRepository doctors;

  public DoctorIdentityService(DoctorRepository doctors) {
    this.doctors = doctors;
  }

  @Transactional(readOnly = true)
  public Doctor load(Long userId) {
    Doctor d =
        doctors
            .findByUserId(userId)
            .orElseThrow(() -> new ApiException(403, "Khong co ho so Doctor"));
    DoctorPolicy.requireAccount(d);
    return d;
  }

  @Transactional(readOnly = true)
  public Doctor approved(DoctorPrincipal p) {
    Doctor d = load(p.userId());
    if (!d.getId().equals(p.doctorId())) throw new ApiException(403, "Danh tinh khong hop le");
    DoctorPolicy.requireApproved(d);
    return d;
  }
}

package com.blockchaincb.demo.security;

import com.blockchaincb.demo.entity.Doctor;
import com.blockchaincb.demo.exception.ApiException;
import java.util.Set;

public final class DoctorPolicy {
  private DoctorPolicy() {}

  public static void requireAccount(Doctor d) {
    if (!"DOCTOR".equals(d.getUser().getRole())
        || !d.getUser().isEnabled()
        || "BLOCKED".equals(d.getStatus())
        || "BLOCKED".equals(d.getApprovalStatus()))
      throw new ApiException(403, "Tai khoan khong duoc phep truy cap");
    if (d.getApprovalStatus() == null
        || !Set.of("PENDING", "APPROVED", "REJECTED").contains(d.getApprovalStatus()))
      throw new ApiException(403, "Trang thai phe duyet khong hop le");
  }

  public static void requireApproved(Doctor d) {
    requireAccount(d);
    if (!"APPROVED".equals(d.getApprovalStatus()))
      throw new ApiException(403, "Bac si chua duoc phe duyet");
  }
}

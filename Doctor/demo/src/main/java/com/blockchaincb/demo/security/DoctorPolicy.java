package com.blockchaincb.demo.security;

import com.blockchaincb.demo.entity.Doctor;
import com.blockchaincb.demo.exception.ApiException;
import java.util.Set;

public final class DoctorPolicy {
  private DoctorPolicy() {}

  public static void requireAccount(Doctor d) {
    if (d == null || d.getUser() == null)
      throw new ApiException(403, "DOCTOR_PROFILE_INVALID", "Ho so Doctor khong hop le");
    if (!"DOCTOR".equals(d.getUser().getRole()))
      throw new ApiException(403, "ACCOUNT_ROLE_INVALID", "Tai khoan khong phai Doctor");
    if (!d.getUser().isEnabled())
      throw new ApiException(403, "ACCOUNT_DISABLED", "Tai khoan da bi vo hieu hoa");
    if ("BLOCKED".equals(d.getStatus()) || "BLOCKED".equals(d.getApprovalStatus()))
      throw new ApiException(403, "DOCTOR_BLOCKED", "Tai khoan Doctor da bi khoa");
    if (d.getApprovalStatus() == null
        || !Set.of("PENDING", "APPROVED", "REJECTED").contains(d.getApprovalStatus()))
      throw new ApiException(403, "DOCTOR_APPROVAL_INVALID", "Trang thai phe duyet khong hop le");
  }

  public static void requireApproved(Doctor d) {
    requireAccount(d);
    if (!"APPROVED".equals(d.getApprovalStatus()))
      throw new ApiException(403, "DOCTOR_APPROVAL_REQUIRED", "Bac si chua duoc phe duyet");
  }

  /** Ownership proof is allowed before approval; it does not grant medical/chain permissions. */
  public static void requireWalletLink(Doctor d) {
    requireAccount(d);
    if (!Set.of("PENDING", "APPROVED").contains(d.getApprovalStatus()))
      throw new ApiException(403, "WALLET_LINK_NOT_ALLOWED");
  }
}

package com.blockchaincb.demo.dto;

import com.blockchaincb.demo.entity.Doctor;

public record DoctorResponse(
    Long id,
    Long userId,
    String email,
    String fullName,
    String hospital,
    Long specialtyId,
    String licenseNumber,
    String status,
    String approvalStatus,
    String walletAddress) {
  public static DoctorResponse from(Doctor d) {
    return new DoctorResponse(
        d.getId(),
        d.getUser().getId(),
        d.getUser().getEmail(),
        d.getUser().getFullName(),
        d.getHospital(),
        d.getSpecialtyId(),
        d.getLicenseNumber(),
        d.getStatus(),
        d.getApprovalStatus(),
        d.getUser().getWalletAddress());
  }
}

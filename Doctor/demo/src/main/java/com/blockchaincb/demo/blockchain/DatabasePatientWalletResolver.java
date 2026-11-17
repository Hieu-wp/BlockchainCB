package com.blockchaincb.demo.blockchain;

import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.*;
import org.springframework.stereotype.Service;

@Service
public class DatabasePatientWalletResolver implements PatientWalletResolver {
  private final PatientRepository patients;
  private final UserRepository users;
  private final WalletLinkService wallets;

  public DatabasePatientWalletResolver(
      PatientRepository patients, UserRepository users, WalletLinkService wallets) {
    this.patients = patients;
    this.users = users;
    this.wallets = wallets;
  }

  public String verifiedWallet(Long patientId) {
    var p =
        patients
            .findById(patientId)
            .orElseThrow(() -> new ApiException(409, "PATIENT_LINK_MISSING"));
    var u =
        users
            .findById(p.getUserId())
            .orElseThrow(() -> new ApiException(409, "PATIENT_USER_MISSING"));
    if (!"PATIENT".equals(u.getRole())) throw new ApiException(409, "PATIENT_IDENTITY_MISMATCH");
    if (u.getWalletAddress() == null) throw new ApiException(409, "PATIENT_WALLET_MISSING");
    return wallets.requiredWallet(u.getId(), u.getWalletAddress());
  }
}

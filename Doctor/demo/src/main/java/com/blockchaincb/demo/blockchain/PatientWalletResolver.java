package com.blockchaincb.demo.blockchain;

/** Patient team integration seam: must return a wallet with independently verified ownership. */
public interface PatientWalletResolver {
  String verifiedWallet(Long patientId);
}

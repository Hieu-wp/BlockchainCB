package com.blockchaincb.demo.blockchain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wallet_bindings", schema = "doctor_backend")
public class WalletBinding {
  @Id public Long userId;

  @Column(nullable = false, unique = true, length = 42)
  public String address;

  @Column(nullable = false)
  public long chainId;

  @Column(nullable = false)
  public Instant verifiedAt;

  @Column(nullable = false)
  public String proofMethod;
}

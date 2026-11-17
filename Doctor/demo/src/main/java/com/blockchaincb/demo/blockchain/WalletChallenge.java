package com.blockchaincb.demo.blockchain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wallet_challenges", schema = "doctor_backend")
public class WalletChallenge {
  @Id public String id;

  @Column(nullable = false)
  public Long userId;

  @Column(nullable = false)
  public Long doctorId;

  @Column(nullable = false, length = 42)
  public String address;

  @Column(nullable = false, length = 2048)
  public String message;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant consumedAt;
}

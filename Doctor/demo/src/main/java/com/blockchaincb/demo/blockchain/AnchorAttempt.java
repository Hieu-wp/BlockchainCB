package com.blockchaincb.demo.blockchain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "anchor_attempts", schema = "doctor_backend")
public class AnchorAttempt {
  @Id public String id;

  @Column(nullable = false)
  public Long recordId;

  @Column(nullable = false)
  public Long versionId;

  @Column(nullable = false)
  public int versionNumber;

  @Column(nullable = false)
  public Long userId;

  @Column(nullable = false)
  public Long doctorId;

  @Column(nullable = false, length = 66)
  public String recordKey;

  @Column(nullable = false, length = 42)
  public String patientWallet;

  @Column(nullable = false, length = 42)
  public String doctorWallet;

  @Column(nullable = false, length = 66)
  public String contentHash;

  @Column(nullable = false, length = 66)
  public String previousHash;

  @Column(unique = true, length = 66)
  public String transactionHash;

  @Column(nullable = false)
  public String status;

  @Column(nullable = false)
  public String code;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant checkedAt;

  @Column(nullable = false)
  public long scanFrom;

  @Column(nullable = false)
  public int pollCount;

  public Long blockNumber;

  @Column(length = 66)
  public String blockHash;

  public AnchorGateway.Payload payload() {
    return new AnchorGateway.Payload(
        recordKey,
        patientWallet,
        versionNumber,
        contentHash,
        previousHash,
        ChainSettings.CONTRACT,
        ChainSettings.CHAIN,
        doctorWallet);
  }
}

package com.blockchaincb.demo.blockchain;

import jakarta.persistence.*;

@Entity
@Table(name = "record_anchors", schema = "doctor_backend")
public class RecordAnchor {
  @Id public Long recordId;

  @Column(nullable = false, unique = true, length = 66)
  public String recordKey;

  @Column(nullable = false, length = 42)
  public String patientWallet;
}

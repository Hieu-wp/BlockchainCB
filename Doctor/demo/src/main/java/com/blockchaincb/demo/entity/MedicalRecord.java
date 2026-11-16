package com.blockchaincb.demo.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "medical_records", schema = "doctor_backend")
public class MedicalRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  public Long getId() {
    return id;
  }

  @Column(nullable = false, updatable = false)
  private Long patientId;

  public Long getPatientId() {
    return patientId;
  }

  public void setPatientId(Long value) {
    this.patientId = value;
  }

  @Column(nullable = false, updatable = false)
  private Long createdBy;

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long value) {
    this.createdBy = value;
  }

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant value) {
    this.createdAt = value;
  }

  @Column(nullable = false)
  private Instant updatedAt;

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant value) {
    this.updatedAt = value;
  }

  @Column(nullable = false)
  private int currentVersion;

  public int getCurrentVersion() {
    return currentVersion;
  }

  public void setCurrentVersion(int value) {
    this.currentVersion = value;
  }

  @Version private Long lockVersion;

  public Long getLockVersion() {
    return lockVersion;
  }

  public void setLockVersion(Long value) {
    this.lockVersion = value;
  }
}

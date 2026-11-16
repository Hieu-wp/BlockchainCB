package com.blockchaincb.demo.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "access_grants", schema = "doctor_backend")
public class AccessGrant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  public Long getId() {
    return id;
  }

  @Column(nullable = false)
  private Long patientId;

  public Long getPatientId() {
    return patientId;
  }

  public void setPatientId(Long value) {
    this.patientId = value;
  }

  @Column(nullable = false)
  private Long doctorId;

  public Long getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(Long value) {
    this.doctorId = value;
  }

  @Column(nullable = false)
  private boolean canRead;

  public boolean getCanRead() {
    return canRead;
  }

  public void setCanRead(boolean value) {
    this.canRead = value;
  }

  @Column(nullable = false)
  private boolean canCreate;

  public boolean getCanCreate() {
    return canCreate;
  }

  public void setCanCreate(boolean value) {
    this.canCreate = value;
  }

  @Column(nullable = false)
  private boolean canUpdate;

  public boolean getCanUpdate() {
    return canUpdate;
  }

  public void setCanUpdate(boolean value) {
    this.canUpdate = value;
  }

  @Column(nullable = false)
  private Instant validFrom;

  public Instant getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(Instant value) {
    this.validFrom = value;
  }

  private Instant expiresAt;

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant value) {
    this.expiresAt = value;
  }

  private Instant revokedAt;

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant value) {
    this.revokedAt = value;
  }
}

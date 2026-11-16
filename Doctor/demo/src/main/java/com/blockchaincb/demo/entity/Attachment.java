package com.blockchaincb.demo.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "attachments", schema = "doctor_backend")
public class Attachment {
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
  private Long uploadedBy;

  public Long getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(Long value) {
    this.uploadedBy = value;
  }

  @Column(nullable = false, unique = true, updatable = false)
  private String storageKey;

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String value) {
    this.storageKey = value;
  }

  @Column(nullable = false, updatable = false)
  private String mediaType;

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String value) {
    this.mediaType = value;
  }

  @Column(nullable = false, updatable = false)
  private long sizeBytes;

  public long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(long value) {
    this.sizeBytes = value;
  }

  @Column(nullable = false, length = 64, updatable = false)
  private String sha256;

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String value) {
    this.sha256 = value;
  }

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant value) {
    this.createdAt = value;
  }
}

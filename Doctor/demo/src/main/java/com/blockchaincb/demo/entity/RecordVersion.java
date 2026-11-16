package com.blockchaincb.demo.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@org.hibernate.annotations.Immutable
@Table(
    name = "record_versions",
    schema = "doctor_backend",
    uniqueConstraints = @UniqueConstraint(columnNames = {"record_id", "version_number"}))
public class RecordVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  public Long getId() {
    return id;
  }

  @Column(nullable = false, updatable = false)
  private Long recordId;

  public Long getRecordId() {
    return recordId;
  }

  public void setRecordId(Long value) {
    this.recordId = value;
  }

  @Column(nullable = false, updatable = false)
  private int versionNumber;

  public int getVersionNumber() {
    return versionNumber;
  }

  public void setVersionNumber(int value) {
    this.versionNumber = value;
  }

  @Column(nullable = false, updatable = false)
  private Long doctorId;

  public Long getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(Long value) {
    this.doctorId = value;
  }

  @Column(updatable = false)
  private Long previousVersionId;

  public Long getPreviousVersionId() {
    return previousVersionId;
  }

  public void setPreviousVersionId(Long value) {
    this.previousVersionId = value;
  }

  @Column(length = 64, updatable = false)
  private String previousHash;

  public String getPreviousHash() {
    return previousHash;
  }

  public void setPreviousHash(String value) {
    this.previousHash = value;
  }

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant value) {
    this.createdAt = value;
  }

  @Column(nullable = false, updatable = false)
  private LocalDate examDate;

  public LocalDate getExamDate() {
    return examDate;
  }

  public void setExamDate(LocalDate value) {
    this.examDate = value;
  }

  @Column(nullable = false, length = 10000, updatable = false)
  private String diagnosis;

  public String getDiagnosis() {
    return diagnosis;
  }

  public void setDiagnosis(String value) {
    this.diagnosis = value;
  }

  @Column(nullable = false, length = 10000, updatable = false)
  private String prescription;

  public String getPrescription() {
    return prescription;
  }

  public void setPrescription(String value) {
    this.prescription = value;
  }

  @Column(nullable = false, length = 10000, updatable = false)
  private String testResults;

  public String getTestResults() {
    return testResults;
  }

  public void setTestResults(String value) {
    this.testResults = value;
  }

  @Column(nullable = false, length = 10000, updatable = false)
  private String notes;

  public String getNotes() {
    return notes;
  }

  public void setNotes(String value) {
    this.notes = value;
  }

  @Column(nullable = false, length = 100000, updatable = false)
  private String canonicalContent;

  public String getCanonicalContent() {
    return canonicalContent;
  }

  public void setCanonicalContent(String value) {
    this.canonicalContent = value;
  }

  @Column(nullable = false, length = 64, updatable = false)
  private String contentHash;

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String value) {
    this.contentHash = value;
  }

  @Column(nullable = false, updatable = false)
  private String canonicalSchema;

  public String getCanonicalSchema() {
    return canonicalSchema;
  }

  public void setCanonicalSchema(String value) {
    this.canonicalSchema = value;
  }

  @Column(nullable = false, updatable = false)
  private String blockchainStatus;

  public String getBlockchainStatus() {
    return blockchainStatus;
  }

  public void setBlockchainStatus(String value) {
    this.blockchainStatus = value;
  }

  @Column(updatable = false)
  private String blockchainTransactionHash;

  public String getBlockchainTransactionHash() {
    return blockchainTransactionHash;
  }

  public void setBlockchainTransactionHash(String value) {
    this.blockchainTransactionHash = value;
  }

  @Column(updatable = false)
  private String blockchainRecordId;

  public String getBlockchainRecordId() {
    return blockchainRecordId;
  }

  public void setBlockchainRecordId(String value) {
    this.blockchainRecordId = value;
  }

  @ElementCollection
  @CollectionTable(
      name = "version_attachments",
      schema = "doctor_backend",
      joinColumns = @JoinColumn(name = "version_id"))
  @OrderColumn(name = "position")
  @Column(name = "attachment_id")
  private List<Long> attachmentIds;

  public List<Long> getAttachmentIds() {
    return attachmentIds;
  }

  public void setAttachmentIds(List<Long> value) {
    this.attachmentIds = value;
  }
}

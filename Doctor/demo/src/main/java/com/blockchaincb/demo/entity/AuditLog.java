package com.blockchaincb.demo.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "audit_logs", schema = "doctor_backend")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  public Long getId() {
    return id;
  }

  @Column(nullable = false)
  private Long actorUserId;

  public Long getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(Long value) {
    this.actorUserId = value;
  }

  @Column(nullable = false, length = 32)
  private String action;

  public String getAction() {
    return action;
  }

  public void setAction(String value) {
    this.action = value;
  }

  @Column(nullable = false, length = 200)
  private String resource;

  public String getResource() {
    return resource;
  }

  public void setResource(String value) {
    this.resource = value;
  }

  @Column(nullable = false, length = 32)
  private String outcome;

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String value) {
    this.outcome = value;
  }

  @Column(nullable = false)
  private Instant occurredAt;

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant value) {
    this.occurredAt = value;
  }
}

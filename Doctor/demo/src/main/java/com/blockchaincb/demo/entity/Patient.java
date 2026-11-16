package com.blockchaincb.demo.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "patient_links", schema = "doctor_backend")
public class Patient {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  public Long getId() {
    return id;
  }

  @Column(nullable = false, unique = true)
  private Long userId;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long value) {
    this.userId = value;
  }
}

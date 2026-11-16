package com.blockchaincb.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "specialties")
public class Specialty {
  @Id private Long id;
  private String code;
  private String name;

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}

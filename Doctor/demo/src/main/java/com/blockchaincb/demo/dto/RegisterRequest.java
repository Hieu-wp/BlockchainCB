package com.blockchaincb.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
  @NotBlank(message = "Ho ten khong duoc de trong")
  @Size(max = 255)
  private String fullName;

  @NotBlank(message = "Email khong duoc de trong")
  @Email(message = "Email khong hop le")
  @Size(max = 255)
  private String email;

  @NotBlank(message = "Mat khau khong duoc de trong")
  @Size(min = 8, max = 72, message = "Mat khau phai co it nhat 8 ky tu")
  private String password;

  @NotBlank(message = "So chung chi khong duoc de trong")
  @Size(max = 255)
  private String licenseNumber;

  @NotBlank(message = "Benh vien khong duoc de trong")
  @Size(max = 255)
  private String hospital;

  @jakarta.validation.constraints.Positive
  @NotNull(message = "Vui long chon chuyen khoa")
  private Long specialtyId;

  public RegisterRequest() {}

  public Long getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(Long specialtyId) {
    this.specialtyId = specialtyId;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getLicenseNumber() {
    return licenseNumber;
  }

  public void setLicenseNumber(String licenseNumber) {
    this.licenseNumber = licenseNumber;
  }

  public String getHospital() {
    return hospital;
  }

  public void setHospital(String hospital) {
    this.hospital = hospital;
  }
}

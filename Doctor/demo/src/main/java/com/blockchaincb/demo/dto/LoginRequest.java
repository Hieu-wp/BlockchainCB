package com.blockchaincb.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

  @NotBlank(message = "Email khong duoc de trong")
  @Email(message = "Email khong hop le")
  @jakarta.validation.constraints.Size(max = 255)
  private String email;

  @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
  @NotBlank(message = "Mat khau khong duoc de trong")
  private String password;

  public LoginRequest() {}

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
}

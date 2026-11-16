package com.blockchaincb.demo.controller;

import com.blockchaincb.demo.dto.*;
import com.blockchaincb.demo.security.JwtService;
import com.blockchaincb.demo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/doctor")
public class AuthController {
  private final AuthService auth;
  private final JwtService jwt;

  public AuthController(AuthService auth, JwtService jwt) {
    this.auth = auth;
    this.jwt = jwt;
  }

  public record RegistrationResponse(
      String message, Long id, String email, String status, String approvalStatus) {}

  public record LoginResponse(
      String message,
      String token,
      String tokenType,
      Long id,
      String fullName,
      String email,
      String status,
      String approvalStatus) {
    @Override
    public String toString() {
      return "LoginResponse[REDACTED]";
    }
  }

  @PostMapping("/register")
  public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
    var d = auth.register(request);
    return new RegistrationResponse(
        "Dang ky thanh cong", d.getId(), d.getEmail(), d.getStatus(), d.getApprovalStatus());
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    var d = auth.login(request);
    return new LoginResponse(
        "Dang nhap thanh cong",
        jwt.generateToken(d),
        "Bearer",
        d.getId(),
        d.getUser().getFullName(),
        d.getUser().getEmail(),
        d.getStatus(),
        d.getApprovalStatus());
  }
}

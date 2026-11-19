package com.blockchaincb.demo.controller;

import com.blockchaincb.demo.dto.*;
import com.blockchaincb.demo.security.JwtService;
import com.blockchaincb.demo.security.AuthPayloadService;
import com.blockchaincb.demo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/doctor")
public class AuthController {
  private final AuthService auth;
  private final JwtService jwt;
  private final AuthPayloadService encryption;

  public AuthController(AuthService auth, JwtService jwt, AuthPayloadService encryption) {
    this.auth = auth;
    this.jwt = jwt;
    this.encryption = encryption;
  }

  public record Envelope(
      @jakarta.validation.constraints.NotBlank
      @jakarta.validation.constraints.Size(max = 16384) String payload) {
    @Override public String toString() { return "AuthEnvelope[REDACTED]"; }
  }

  @GetMapping("/encryption-challenge")
  public org.springframework.http.ResponseEntity<AuthPayloadService.Challenge> challenge(
      @RequestParam String purpose) {
    return org.springframework.http.ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(encryption.challenge(purpose));
  }

  @PostMapping("/login/encrypted")
  public LoginResponse encryptedLogin(@Valid @RequestBody Envelope envelope) {
    return login(encryption.open(envelope.payload(), "login", LoginRequest.class));
  }

  @PostMapping("/register/encrypted")
  public RegistrationResponse encryptedRegister(@Valid @RequestBody Envelope envelope) {
    return register(encryption.open(envelope.payload(), "register", RegisterRequest.class));
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
public RegistrationResponse register(
        @Valid @RequestBody RegisterRequest request
) {
    var d = auth.register(request);

    return new RegistrationResponse(
        "Dang ky thanh cong",
        d.getId(),
        d.getEmail(),
        d.getStatus(),
        d.getApprovalStatus()
    );
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

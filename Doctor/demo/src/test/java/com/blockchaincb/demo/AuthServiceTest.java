package com.blockchaincb.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.blockchaincb.demo.dto.*;
import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.*;
import com.blockchaincb.demo.service.AuthService;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  @Mock UserRepository users;
  @Mock DoctorRepository doctors;
  @Mock SpecialtyRepository specialties;
  @Mock PasswordEncoder encoder;
  AuthService service;
  User user;
  Doctor doctor;
  LoginRequest request;

  @BeforeEach
  void setup() {
    service = new AuthService(doctors, users, encoder, specialties);
    user = new User();
    user.setId(1L);
    user.setEmail("doctor@example.test");
    user.setRole("DOCTOR");
    user.setPasswordHash("shared-hash");
    doctor = new Doctor();
    doctor.setUser(user);
    doctor.setPassword("legacy-hash");
    request = new LoginRequest();
    request.setEmail(user.getEmail());
    request.setPassword("password");
  }

  void found(boolean valid) {
    when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    when(encoder.matches("password", "shared-hash")).thenReturn(valid);
  }

  @Test
  void usesSharedPasswordAndAllowsPending() {
    found(true);
    when(doctors.findByUserId(1L)).thenReturn(Optional.of(doctor));
    assertSame(doctor, service.login(request));
    verify(encoder).matches("password", "shared-hash");
    verify(doctors, never()).findByEmail(anyString());
  }

  @Test
  void rejectsWrongPassword() {
    found(false);
    assertEquals(401, assertThrows(ApiException.class, () -> service.login(request)).getStatus());
    verifyNoInteractions(doctors);
  }

  @Test
  void rejectsDisabled() {
    found(true);
    user.setEnabled(false);
    when(doctors.findByUserId(1L)).thenReturn(Optional.of(doctor));
    assertEquals(403, assertThrows(ApiException.class, () -> service.login(request)).getStatus());
  }

  @Test
  void rejectsPatientRoleEvenWithDoctorRow() {
    found(true);
    user.setRole("PATIENT");
    when(doctors.findByUserId(1L)).thenReturn(Optional.of(doctor));
    assertEquals(403, assertThrows(ApiException.class, () -> service.login(request)).getStatus());
  }

  @Test
  void invalidSpecialtyDoesNotSaveUser() {
    var r = new RegisterRequest();
    r.setSpecialtyId(999L);
    assertEquals(400, assertThrows(ApiException.class, () -> service.register(r)).getStatus());
    verifyNoInteractions(users, doctors, encoder);
  }

  @Test
  void duplicateEmailDoesNotHashOrSave() {
    var r = new RegisterRequest();
    r.setSpecialtyId(1L);
    r.setEmail(user.getEmail());
    r.setPassword("Abc@123456");
    when(specialties.existsById(1L)).thenReturn(true);
    when(users.existsByEmail(user.getEmail())).thenReturn(true);
    assertEquals(409, assertThrows(ApiException.class, () -> service.register(r)).getStatus());
    verifyNoInteractions(encoder, doctors);
  }
}

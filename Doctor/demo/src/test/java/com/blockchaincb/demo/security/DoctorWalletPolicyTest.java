package com.blockchaincb.demo.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.DoctorRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DoctorWalletPolicyTest {
  private Doctor doctor(String approval) {
    var d = new Doctor();
    org.springframework.test.util.ReflectionTestUtils.setField(d, "id", 10L);
    d.setApprovalStatus(approval);
    var u = new User();
    u.setId(1L);
    u.setEnabled(true);
    u.setRole("DOCTOR");
    d.setUser(u);
    return d;
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "APPROVED"})
  void ownershipProofHasSeparateApprovalPolicy(String approval) {
    var d = doctor(approval);
    DoctorPolicy.requireWalletLink(d);
    assertEquals(approval, d.getApprovalStatus());
    if ("PENDING".equals(approval))
      assertEquals(
          403, assertThrows(ApiException.class, () -> DoctorPolicy.requireApproved(d)).getStatus());
    else assertDoesNotThrow(() -> DoctorPolicy.requireApproved(d));
  }

  @ParameterizedTest
  @ValueSource(strings = {"REJECTED", "BLOCKED", "UNKNOWN", ""})
  void otherApprovalStatesCannotLink(String approval) {
    assertEquals(
        403,
        assertThrows(ApiException.class, () -> DoctorPolicy.requireWalletLink(doctor(approval)))
            .getStatus());
  }

  @Test
  void disabledLegacyBlockedWrongRoleOrNullStateCannotLink() {
    var d = doctor("PENDING");
    d.getUser().setEnabled(false);
    assertThrows(ApiException.class, () -> DoctorPolicy.requireWalletLink(d));
    d.getUser().setEnabled(true);
    d.setStatus("BLOCKED");
    assertThrows(ApiException.class, () -> DoctorPolicy.requireWalletLink(d));
    d.setStatus("PENDING");
    d.getUser().setRole("PATIENT");
    assertThrows(ApiException.class, () -> DoctorPolicy.requireWalletLink(d));
    d.getUser().setRole("DOCTOR");
    d.setApprovalStatus(null);
    assertThrows(ApiException.class, () -> DoctorPolicy.requireWalletLink(d));
  }

  @Test
  void identityMustMatchCurrentDoctorAndUser() {
    var repository = mock(DoctorRepository.class);
    when(repository.findByUserId(1L)).thenReturn(Optional.of(doctor("PENDING")));
    var identities = new DoctorIdentityService(repository);
    assertEquals(10L, identities.walletLinkAllowed(new DoctorPrincipal(1L, 10L)).getId());
    assertThrows(
        ApiException.class, () -> identities.walletLinkAllowed(new DoctorPrincipal(1L, 11L)));
    assertThrows(
        ApiException.class, () -> identities.walletLinkAllowed(new DoctorPrincipal(2L, 10L)));
  }
}

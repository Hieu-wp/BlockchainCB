package com.blockchaincb.demo.blockchain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.blockchaincb.demo.dto.MedicalRecordResponse.VersionResponse;
import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.*;
import com.blockchaincb.demo.security.*;
import com.blockchaincb.demo.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnchorServiceTest {
  @Mock MedicalRecordService medical;
  @Mock MedicalRecordRepository records;
  @Mock RecordAnchorRepository anchors;
  @Mock AnchorAttemptRepository attempts;
  @Mock DoctorIdentityService identities;
  @Mock WalletLinkService wallets;
  @Mock PatientWalletResolver patients;
  @Mock PatientAccessPolicy access;
  @Mock AnchorGateway chain;
  AnchorService service;
  DoctorPrincipal actor = new DoctorPrincipal(1L, 10L);
  static final String DOCTOR = "0x" + "1".repeat(40),
      PATIENT = "0x" + "2".repeat(40),
      HASH = "0x" + "3".repeat(64),
      KEY = "0x" + "4".repeat(64),
      TX = "0x" + "5".repeat(64);
  VersionResponse v;
  RecordAnchor key;

  @BeforeEach
  void setup() {
    service =
        new AnchorService(
            medical,
            records,
            anchors,
            attempts,
            identities,
            wallets,
            patients,
            access,
            chain,
            new ChainSettings(true, true, "http://127.0.0.1", "localhost", 3));
    var u = new User();
    u.setId(1L);
    u.setWalletAddress(DOCTOR);
    var d = mock(Doctor.class);
    when(d.getUser()).thenReturn(u);
    when(identities.approved(actor)).thenReturn(d);
    var r = new MedicalRecord();
    r.setPatientId(7L);
    when(records.lockForAnchor(9L)).thenReturn(Optional.of(r));
    when(records.findById(9L)).thenReturn(Optional.of(r));
    v = mock(VersionResponse.class);
    when(v.id()).thenReturn(12L);
    when(v.doctorId()).thenReturn(10L);
    when(v.contentHash()).thenReturn(HASH.substring(2));
    when(medical.getVersion(eq(actor), eq(9L), anyInt())).thenReturn(v);
    var h = mock(MedicalRecordService.HashResponse.class);
    when(h.currentContentMatchesHash()).thenReturn(true);
    when(h.storedSnapshotMatchesHash()).thenReturn(true);
    when(h.previousLinkMatches()).thenReturn(true);
    when(h.hash()).thenReturn(HASH.substring(2));
    when(medical.hash(eq(actor), eq(9L), anyInt())).thenReturn(h);
    when(wallets.requiredWallet(1L, DOCTOR)).thenReturn(DOCTOR);
    when(patients.verifiedWallet(7L)).thenReturn(PATIENT);
    key = new RecordAnchor();
    key.recordId = 9L;
    key.recordKey = KEY;
    key.patientWallet = PATIENT;
    when(anchors.findById(9L)).thenReturn(Optional.of(key));
    when(chain.doctorRole(DOCTOR)).thenReturn(true);
    when(chain.writePermission(PATIENT, DOCTOR)).thenReturn(true);
    when(chain.head()).thenReturn(100L);
    when(chain.state(KEY))
        .thenReturn(new AnchorGateway.State("0x" + "0".repeat(40), 0, ChainSettings.ZERO));
  }

  AnchorAttempt attempt() {
    var a = new AnchorAttempt();
    a.id = "attempt";
    a.recordId = 9L;
    a.versionId = 12L;
    a.versionNumber = 1;
    a.userId = 1L;
    a.doctorId = 10L;
    a.doctorWallet = DOCTOR;
    a.patientWallet = PATIENT;
    a.recordKey = KEY;
    a.contentHash = HASH;
    a.previousHash = ChainSettings.ZERO;
    a.status = "PREPARED";
    a.code = "AWAITING_WALLET";
    a.createdAt = Instant.now();
    a.checkedAt = a.createdAt;
    a.scanFrom = 100;
    when(attempts.findFirstByRecordIdAndVersionNumberOrderByCreatedAtDesc(9L, 1))
        .thenReturn(Optional.of(a));
    return a;
  }

  @Test
  void firstPayloadComesFromServerHashAndStableOpaqueKey() {
    var p = service.prepare(actor, 9L, 1);
    assertEquals(KEY, p.recordKey());
    assertEquals(ChainSettings.ZERO, p.previousHash());
    assertEquals(HASH, p.contentHash());
    assertEquals(PATIENT, p.patientWallet());
    service.prepare(actor, 9L, 1);
    assertEquals(KEY, p.recordKey());
  }

  @Test
  void opaqueKeysAreRandomNotPatientOrRecordIdentifiers() {
    var a = AnchorService.opaqueKey();
    var b = AnchorService.opaqueKey();
    assertTrue(a.matches("0x[0-9a-f]{64}"));
    assertNotEquals(a, b);
  }

  @Test
  void nextVersionMustMatchActualPriorOnchainHash() {
    when(v.previousHash()).thenReturn(HASH.substring(2));
    when(chain.state(KEY)).thenReturn(new AnchorGateway.State(PATIENT, 1, HASH));
    when(chain.versionHash(KEY, 1)).thenReturn(HASH);
    assertEquals(HASH, service.prepare(actor, 9L, 2).previousHash());
  }

  @Test
  void rejectsOnchainPreviousMismatch() {
    when(v.previousHash()).thenReturn(HASH.substring(2));
    when(chain.state(KEY)).thenReturn(new AnchorGateway.State(PATIENT, 1, ChainSettings.ZERO));
    assertEquals(
        409, assertThrows(ApiException.class, () -> service.prepare(actor, 9L, 2)).getStatus());
  }

  @Test
  void rejectsSkippingVersion() {
    when(v.previousHash()).thenReturn(HASH.substring(2));
    assertEquals(
        409, assertThrows(ApiException.class, () -> service.prepare(actor, 9L, 2)).getStatus());
  }

  @Test
  void permissionDeniedNeverCallsRpc() {
    doThrow(new ApiException(403, "DENIED"))
        .when(access)
        .require(10L, 7L, PatientAccessPolicy.Action.CREATE);
    assertEquals(
        403, assertThrows(ApiException.class, () -> service.prepare(actor, 9L, 1)).getStatus());
    verifyNoInteractions(chain);
  }

  @Test
  void missingPatientProofNeverUsesDeployer() {
    when(patients.verifiedWallet(7L)).thenThrow(new ApiException(409, "WALLET_PROOF_MISSING"));
    assertEquals(
        409, assertThrows(ApiException.class, () -> service.prepare(actor, 9L, 1)).getStatus());
    verifyNoInteractions(chain);
  }

  @Test
  void missingDoctorRoleAndConsentAreRejected() {
    when(chain.doctorRole(DOCTOR)).thenReturn(false);
    assertEquals(
        403, assertThrows(ApiException.class, () -> service.prepare(actor, 9L, 1)).getStatus());
    when(chain.doctorRole(DOCTOR)).thenReturn(true);
    when(chain.writePermission(PATIENT, DOCTOR)).thenReturn(false);
    assertEquals(
        403, assertThrows(ApiException.class, () -> service.prepare(actor, 9L, 1)).getStatus());
  }

  @Test
  void tamperedContentNeverPrepares() {
    var h = mock(MedicalRecordService.HashResponse.class);
    when(medical.hash(actor, 9L, 1)).thenReturn(h);
    assertEquals(
        409, assertThrows(ApiException.class, () -> service.prepare(actor, 9L, 1)).getStatus());
    verifyNoInteractions(chain);
  }

  @Test
  void malformedTransactionRejected() {
    attempt();
    assertEquals(
        400,
        assertThrows(ApiException.class, () -> service.submit(actor, 9L, 1, "bad")).getStatus());
  }

  @Test
  void pendingIsNotConfirmed() {
    var a = attempt();
    when(chain.verify(a.payload(), TX))
        .thenReturn(new AnchorGateway.Verified("PENDING", "RECEIPT_PENDING", null, null));
    assertEquals("PENDING", service.submit(actor, 9L, 1, TX).status());
    assertEquals(TX, a.transactionHash);
  }

  @Test
  void revertedIsFailed() {
    var a = attempt();
    when(chain.verify(a.payload(), TX))
        .thenReturn(new AnchorGateway.Verified("FAILED", "TRANSACTION_REVERTED", null, null));
    assertEquals("FAILED", service.submit(actor, 9L, 1, TX).status());
  }

  @Test
  void validReceiptIsConfirmedAndRepeatedHashIsIdempotent() {
    var a = attempt();
    when(chain.verify(a.payload(), TX))
        .thenReturn(new AnchorGateway.Verified("CONFIRMED", "VERIFIED", 100L, HASH));
    assertEquals("CONFIRMED", service.submit(actor, 9L, 1, TX).status());
    when(attempts.findByTransactionHash(TX)).thenReturn(Optional.of(a));
    assertEquals("CONFIRMED", service.submit(actor, 9L, 1, TX).status());
    verify(attempts, times(2)).saveAndFlush(a);
  }

  @Test
  void rpcTimeoutPreservesPendingHash() {
    var a = attempt();
    when(chain.verify(a.payload(), TX)).thenThrow(new ApiException(503, "RPC_UNAVAILABLE"));
    assertEquals("PENDING", service.submit(actor, 9L, 1, TX).status());
    assertEquals(TX, a.transactionHash);
  }

  @Test
  void mismatchedTransactionNeverRegisters() {
    var a = attempt();
    when(chain.verify(a.payload(), TX))
        .thenThrow(new ApiException(422, "TRANSACTION_DATA_MISMATCH"));
    assertEquals(
        422, assertThrows(ApiException.class, () -> service.submit(actor, 9L, 1, TX)).getStatus());
    assertNull(a.transactionHash);
    verify(attempts, never()).saveAndFlush(any());
  }

  @Test
  void recoversHashWhenReactClosedAfterBroadcast() {
    var a = attempt();
    when(chain.scan(a.payload(), 100)).thenReturn(new AnchorGateway.Scan(TX, 100));
    when(chain.verify(a.payload(), TX))
        .thenReturn(new AnchorGateway.Verified("CONFIRMED", "VERIFIED", 100L, HASH));
    assertEquals("CONFIRMED", service.status(actor, 9L, 1).status());
    assertEquals(TX, a.transactionHash);
  }

  @Test
  void unreviewedDeploymentNeverCallsRpc() {
    var blocked =
        new AnchorService(
            medical,
            records,
            anchors,
            attempts,
            identities,
            wallets,
            patients,
            access,
            chain,
            new ChainSettings(true, false, "http://127.0.0.1", "localhost", 3));
    assertEquals(
        503, assertThrows(ApiException.class, () -> blocked.prepare(actor, 9L, 1)).getStatus());
    verifyNoInteractions(chain);
  }
}

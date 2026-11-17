package com.blockchaincb.demo.blockchain;

import com.blockchaincb.demo.dto.MedicalRecordResponse.VersionResponse;
import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.MedicalRecordRepository;
import com.blockchaincb.demo.security.*;
import com.blockchaincb.demo.service.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnchorService {
  private final MedicalRecordService medical;
  private final MedicalRecordRepository records;
  private final RecordAnchorRepository anchors;
  private final AnchorAttemptRepository attempts;
  private final DoctorIdentityService identities;
  private final WalletLinkService wallets;
  private final PatientWalletResolver patients;
  private final PatientAccessPolicy access;
  private final AnchorGateway chain;
  private final ChainSettings settings;

  public AnchorService(
      MedicalRecordService medical,
      MedicalRecordRepository records,
      RecordAnchorRepository anchors,
      AnchorAttemptRepository attempts,
      DoctorIdentityService identities,
      WalletLinkService wallets,
      PatientWalletResolver patients,
      PatientAccessPolicy access,
      AnchorGateway chain,
      ChainSettings settings) {
    this.medical = medical;
    this.records = records;
    this.anchors = anchors;
    this.attempts = attempts;
    this.identities = identities;
    this.wallets = wallets;
    this.patients = patients;
    this.access = access;
    this.chain = chain;
    this.settings = settings;
  }

  public record Status(
      String status,
      String code,
      String transactionHash,
      Long blockNumber,
      String blockHash,
      boolean enabled,
      long chainId,
      String contractAddress,
      Instant preparedAt) {}

  public record Prepared(
      String recordKey,
      String patientWallet,
      long versionNumber,
      String contentHash,
      String previousHash,
      String contractAddress,
      long chainId,
      String doctorWallet,
      String attemptId) {}

  private VersionResponse guard(DoctorPrincipal actor, Long id, int number) {
    var doctor = identities.approved(actor);
    var v = medical.getVersion(actor, id, number);
    var record =
        records.lockForAnchor(id).orElseThrow(() -> new ApiException(404, "RECORD_MISSING"));
    access.require(
        actor.doctorId(),
        record.getPatientId(),
        number == 1 ? PatientAccessPolicy.Action.CREATE : PatientAccessPolicy.Action.UPDATE);
    if (!v.doctorId().equals(actor.doctorId()))
      throw new ApiException(403, "ONLY_VERSION_AUTHOR_CAN_ANCHOR");
    var h = medical.hash(actor, id, number);
    if (!h.storedSnapshotMatchesHash()
        || !h.currentContentMatchesHash()
        || !h.previousLinkMatches()) throw new ApiException(409, "CONTENT_INTEGRITY_FAILED");
    return v;
  }

  private AnchorGateway.Payload expected(
      DoctorPrincipal actor, Long id, int number, VersionResponse v) {
    var d = identities.approved(actor);
    if (d.getUser().getWalletAddress() == null)
      throw new ApiException(409, "DOCTOR_WALLET_MISSING");
    String doctor = wallets.requiredWallet(actor.userId(), d.getUser().getWalletAddress());
    var record = records.findById(id).orElseThrow(() -> new ApiException(404, "RECORD_MISSING"));
    String patient = patients.verifiedWallet(record.getPatientId());
    var saved = anchors.findById(id);
    RecordAnchor key;
    if (saved.isPresent()) key = saved.get();
    else {
      key = new RecordAnchor();
      key.recordId = id;
      key.recordKey = opaqueKey();
      key.patientWallet = patient;
      anchors.saveAndFlush(key);
    }
    if (!key.patientWallet.equalsIgnoreCase(patient))
      throw new ApiException(409, "PATIENT_WALLET_CHANGED_REQUIRES_REVIEW");
    String content = ChainSettings.hex32("0x" + v.contentHash());
    if (content.equals(ChainSettings.ZERO)) throw new ApiException(409, "INVALID_CONTENT_HASH");
    String previous =
        number == 1 ? ChainSettings.ZERO : ChainSettings.hex32("0x" + v.previousHash());
    return new AnchorGateway.Payload(
        key.recordKey,
        patient,
        number,
        content,
        previous,
        ChainSettings.CONTRACT,
        ChainSettings.CHAIN,
        doctor);
  }

  public static String opaqueKey() {
    byte[] b = new byte[32];
    new java.security.SecureRandom().nextBytes(b);
    return "0x" + HexFormat.of().formatHex(b);
  }

  public Prepared prepare(DoctorPrincipal actor, Long id, int number) {
    settings.requireEnabled();
    var v = guard(actor, id, number);
    var p = expected(actor, id, number, v);
    var old = attempts.findFirstByRecordIdAndVersionNumberOrderByCreatedAtDesc(id, number);
    if (old.isPresent()) {
      var a = old.get();
      reconcile(a);
      if ("CONFIRMED".equals(a.status) || "PENDING".equals(a.status))
        throw new ApiException(409, "ANCHOR_EXISTS_CHECK_STATUS");
      if ("PREPARED".equals(a.status) && a.createdAt.plusSeconds(120).isAfter(Instant.now()))
        throw new ApiException(409, "ANCHOR_PREPARED_WAIT_OR_RECOVER");
    }
    if (!chain.doctorRole(p.doctorWallet())) throw new ApiException(403, "DOCTOR_ROLE_MISSING");
    if (!chain.writePermission(p.patientWallet(), p.doctorWallet()))
      throw new ApiException(403, "PATIENT_WRITE_PERMISSION_MISSING");
    var state = chain.state(p.recordKey());
    if (state.latestVersion() != number - 1)
      throw new ApiException(409, "ONCHAIN_VERSION_ORDER_MISMATCH");
    if (number > 1
        && (!state.patientWallet().equalsIgnoreCase(p.patientWallet())
            || !state.latestHash().equalsIgnoreCase(p.previousHash())
            || !chain.versionHash(p.recordKey(), number - 1).equalsIgnoreCase(p.previousHash())))
      throw new ApiException(409, "ONCHAIN_PREVIOUS_HASH_MISMATCH");
    var a = new AnchorAttempt();
    a.id = UUID.randomUUID().toString();
    a.recordId = id;
    a.versionId = v.id();
    a.versionNumber = number;
    a.userId = actor.userId();
    a.doctorId = actor.doctorId();
    a.recordKey = p.recordKey();
    a.patientWallet = p.patientWallet();
    a.doctorWallet = p.doctorWallet();
    a.contentHash = p.contentHash();
    a.previousHash = p.previousHash();
    a.status = "PREPARED";
    a.code = "AWAITING_WALLET";
    a.createdAt = Instant.now();
    a.checkedAt = a.createdAt;
    a.scanFrom = chain.head();
    if (old.isPresent()) a.scanFrom = Math.min(a.scanFrom, old.get().scanFrom);
    attempts.saveAndFlush(a);
    return new Prepared(
        p.recordKey(),
        p.patientWallet(),
        p.versionNumber(),
        p.contentHash(),
        p.previousHash(),
        p.contractAddress(),
        p.chainId(),
        p.doctorWallet(),
        a.id);
  }

  public Status submit(DoctorPrincipal actor, Long id, int number, String hash) {
    settings.requireEnabled();
    var v = guard(actor, id, number);
    var p = expected(actor, id, number, v);
    hash = ChainSettings.hex32(hash);
    var a =
        attempts
            .findFirstByRecordIdAndVersionNumberOrderByCreatedAtDesc(id, number)
            .orElseThrow(() -> new ApiException(409, "PREPARE_ANCHOR_FIRST"));
    if (!a.payload().equals(p)) throw new ApiException(409, "ANCHOR_INTENT_CHANGED");
    var known = attempts.findByTransactionHash(hash);
    if (known.isPresent() && !known.get().id.equals(a.id))
      throw new ApiException(409, "TRANSACTION_ALREADY_USED");
    if (a.transactionHash != null && !a.transactionHash.equals(hash))
      throw new ApiException(409, "TRANSACTION_ALREADY_REGISTERED");
    // Bind only after validating any available tx. RPC outage preserves the hash as pending.
    try {
      apply(a, chain.verify(p, hash));
    } catch (ApiException e) {
      if (e.getStatus() != 503) throw e;
      a.status = "PENDING";
      a.code = "RPC_UNAVAILABLE";
    }
    a.transactionHash = hash;
    a.checkedAt = Instant.now();
    attempts.saveAndFlush(a);
    return response(a);
  }

  public Status status(DoctorPrincipal actor, Long id, int number) {
    // Status reads require READ, not a new grant to send a transaction.
    medical.getVersion(actor, id, number);
    records.lockForAnchor(id).orElseThrow(() -> new ApiException(404, "RECORD_MISSING"));
    var a = attempts.findFirstByRecordIdAndVersionNumberOrderByCreatedAtDesc(id, number);
    if (a.isEmpty())
      return new Status(
          "NOT_SUBMITTED",
          settings.enabled && settings.reviewed ? "NOT_SUBMITTED" : "BLOCKCHAIN_DISABLED",
          null,
          null,
          null,
          settings.enabled && settings.reviewed && !settings.rpc.isBlank(),
          ChainSettings.CHAIN,
          ChainSettings.CONTRACT,
          null);
    var integrity = medical.hash(actor, id, number);
    if (!integrity.currentContentMatchesHash()
        || !integrity.storedSnapshotMatchesHash()
        || !integrity.previousLinkMatches()
        || !("0x" + integrity.hash()).equals(a.get().contentHash))
      throw new ApiException(409, "CONTENT_INTEGRITY_FAILED");
    if (settings.enabled && settings.reviewed && !settings.rpc.isBlank()) reconcile(a.get());
    return response(a.get());
  }

  private void reconcile(AnchorAttempt a) {
    try {
      if (a.transactionHash == null) {
        var scan = chain.scan(a.payload(), a.scanFrom);
        a.scanFrom = scan.nextBlock();
        if (scan.transactionHash() != null) {
          var result = chain.verify(a.payload(), scan.transactionHash());
          var existing = attempts.findByTransactionHash(scan.transactionHash());
          if (existing.isPresent() && !existing.get().id.equals(a.id))
            throw new ApiException(409, "RECOVERED_TRANSACTION_ALREADY_REGISTERED");
          a.transactionHash = scan.transactionHash();
          apply(a, result);
        }
      } else apply(a, chain.verify(a.payload(), a.transactionHash));
    } catch (ApiException e) {
      if (e.getStatus() == 503) {
        a.code = "RPC_UNAVAILABLE";
        if (!"PREPARED".equals(a.status)) a.status = "PENDING";
      } else {
        a.code = "VERIFICATION_MISMATCH";
        a.status = "FAILED";
      }
    }
    a.checkedAt = Instant.now();
    a.pollCount++;
  }

  private void apply(AnchorAttempt a, AnchorGateway.Verified result) {
    a.status = result.status();
    a.code = result.code();
    a.blockNumber = result.blockNumber();
    a.blockHash = result.blockHash();
  }

  private Status response(AnchorAttempt a) {
    return new Status(
        a.status,
        a.code,
        a.transactionHash,
        a.blockNumber,
        a.blockHash,
        settings.enabled && settings.reviewed && !settings.rpc.isBlank(),
        ChainSettings.CHAIN,
        ChainSettings.CONTRACT,
        a.createdAt);
  }
}

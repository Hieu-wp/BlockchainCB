package com.blockchaincb.demo.service;

import com.blockchaincb.demo.dto.*;
import com.blockchaincb.demo.dto.MedicalRecordResponse.VersionResponse;
import com.blockchaincb.demo.entity.*;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.*;
import com.blockchaincb.demo.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MedicalRecordService {
  private final MedicalRecordRepository records;
  private final RecordVersionRepository versions;
  private final DoctorIdentityService identities;
  private final PatientAccessPolicy access;
  private final AttachmentService attachments;
  private final HashService hash;

  public MedicalRecordService(
      MedicalRecordRepository records,
      RecordVersionRepository versions,
      DoctorIdentityService identities,
      PatientAccessPolicy access,
      AttachmentService attachments,
      HashService hash) {
    this.records = records;
    this.versions = versions;
    this.identities = identities;
    this.access = access;
    this.attachments = attachments;
    this.hash = hash;
  }

  private Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private MedicalRecord allowed(DoctorPrincipal actor, Long id, PatientAccessPolicy.Action action) {
    identities.approved(actor);
    var r = records.findById(id).orElseThrow(() -> new ApiException(404, "Khong tim thay ho so"));
    access.require(actor.doctorId(), r.getPatientId(), action);
    return r;
  }

  private RecordVersion version(MedicalRecord r, int number) {
    return versions
        .findByRecordIdAndVersionNumber(r.getId(), number)
        .orElseThrow(() -> new ApiException(404, "Khong tim thay phien ban"));
  }

  public MedicalRecordResponse create(DoctorPrincipal actor, MedicalRecordRequest req) {
    identities.approved(actor);
    access.require(actor.doctorId(), req.patientId(), PatientAccessPolicy.Action.READ);
    access.require(actor.doctorId(), req.patientId(), PatientAccessPolicy.Action.CREATE);
    var r = new MedicalRecord();
    r.setPatientId(req.patientId());
    r.setCreatedBy(actor.doctorId());
    r.setCreatedAt(now());
    r.setUpdatedAt(r.getCreatedAt());
    r.setCurrentVersion(1);
    records.saveAndFlush(r);
    var v = append(r, actor, req, null);
    return response(r, v);
  }

  public MedicalRecordResponse update(DoctorPrincipal actor, Long id, UpdateRecordRequest req) {
    var r = allowed(actor, id, PatientAccessPolicy.Action.UPDATE);
    access.require(actor.doctorId(), r.getPatientId(), PatientAccessPolicy.Action.READ);
    if (!r.getPatientId().equals(req.content().patientId()))
      throw new ApiException(400, "Khong duoc doi benh nhan");
    if (r.getCurrentVersion() != req.expectedVersion())
      throw new ApiException(409, "Phien ban da thay doi");
    var previous = version(r, r.getCurrentVersion());
    if (!hash.sha256(canonical(r, previous)).equals(previous.getContentHash())
        || !hash.sha256(previous.getCanonicalContent()).equals(previous.getContentHash()))
      throw new ApiException(409, "Phien ban truoc khong con nguyen ven");
    r.setCurrentVersion(r.getCurrentVersion() + 1);
    r.setUpdatedAt(now());
    // Optimistic root lock is flushed before appending the new immutable version.
    records.flush();
    return response(r, append(r, actor, req.content(), previous));
  }

  private RecordVersion append(
      MedicalRecord r, DoctorPrincipal actor, MedicalRecordRequest req, RecordVersion previous) {
    attachments.forVersion(
        actor.doctorId(),
        r.getPatientId(),
        req.attachmentIds(),
        previous == null ? Set.of() : Set.copyOf(previous.getAttachmentIds()));
    var v = new RecordVersion();
    v.setRecordId(r.getId());
    v.setVersionNumber(r.getCurrentVersion());
    v.setDoctorId(actor.doctorId());
    v.setCreatedAt(r.getUpdatedAt());
    v.setExamDate(req.examDate());
    v.setDiagnosis(hash.normalize(req.diagnosis()));
    v.setPrescription(hash.normalize(req.prescription()));
    v.setTestResults(hash.normalize(req.testResults()));
    v.setNotes(hash.normalize(req.notes()));
    v.setAttachmentIds(new ArrayList<>(req.attachmentIds()));
    if (previous != null) {
      v.setPreviousVersionId(previous.getId());
      v.setPreviousHash(previous.getContentHash());
    }
    v.setCanonicalSchema(HashService.SCHEMA);
    v.setBlockchainStatus("NOT_SUBMITTED");
    String canonical = canonical(r, v);
    v.setCanonicalContent(canonical);
    v.setContentHash(hash.sha256(canonical));
    return versions.saveAndFlush(v);
  }

  private String canonical(MedicalRecord r, RecordVersion v) {
    var files = attachments.descriptors(r.getPatientId(), v.getAttachmentIds());
    List<Object> fields =
        new ArrayList<>(
            Arrays.asList(
                v.getCanonicalSchema(),
                r.getId(),
                r.getPatientId(),
                v.getVersionNumber(),
                v.getDoctorId(),
                v.getCreatedAt(),
                v.getPreviousVersionId(),
                v.getPreviousHash(),
                v.getExamDate(),
                v.getDiagnosis(),
                v.getPrescription(),
                v.getTestResults(),
                v.getNotes(),
                files.size()));
    for (var file : files) {
      fields.add(file.getId());
      fields.add(file.getSha256());
      fields.add(file.getMediaType());
      fields.add(file.getSizeBytes());
    }
    return hash.canonical(fields.toArray());
  }

  public MedicalRecordResponse get(DoctorPrincipal actor, Long id) {
    var r = allowed(actor, id, PatientAccessPolicy.Action.READ);
    return response(r, version(r, r.getCurrentVersion()));
  }

  public Page<MedicalRecordResponse> patientRecords(
      DoctorPrincipal actor, Long patient, Pageable page) {
    identities.approved(actor);
    access.require(actor.doctorId(), patient, PatientAccessPolicy.Action.READ);
    return records
        .forPatient(patient, page)
        .map(r -> response(r, version(r, r.getCurrentVersion())));
  }

  public Page<MedicalRecordResponse> history(DoctorPrincipal actor, Long patient, Pageable page) {
    identities.approved(actor);
    return records
        .history(actor.doctorId(), patient, now(), page)
        .map(
            r -> {
              access.require(actor.doctorId(), r.getPatientId(), PatientAccessPolicy.Action.READ);
              return response(r, version(r, r.getCurrentVersion()));
            });
  }

  public Page<VersionResponse> versions(DoctorPrincipal actor, Long id, Pageable page) {
    var r = allowed(actor, id, PatientAccessPolicy.Action.READ);
    return versions.findByRecordIdOrderByVersionNumberDesc(r.getId(), page).map(this::response);
  }

  public VersionResponse getVersion(DoctorPrincipal actor, Long id, int number) {
    return response(version(allowed(actor, id, PatientAccessPolicy.Action.READ), number));
  }

  public record HashResponse(
      Long recordId,
      int versionNumber,
      String algorithm,
      String canonicalSchema,
      String hash,
      String previousHash,
      String blockchainStatus,
      boolean storedSnapshotMatchesHash,
      boolean currentContentMatchesHash,
      boolean previousLinkMatches) {}

  public HashResponse hash(DoctorPrincipal actor, Long id, int number) {
    var r = allowed(actor, id, PatientAccessPolicy.Action.READ);
    var v = version(r, number);
    var previous = number > 1 ? version(r, number - 1) : null;
    boolean link =
        previous == null
            ? v.getPreviousVersionId() == null && v.getPreviousHash() == null
            : previous.getId().equals(v.getPreviousVersionId())
                && previous.getContentHash().equals(v.getPreviousHash());
    return new HashResponse(
        id,
        number,
        "SHA-256",
        v.getCanonicalSchema(),
        v.getContentHash(),
        v.getPreviousHash(),
        v.getBlockchainStatus(),
        hash.sha256(v.getCanonicalContent()).equals(v.getContentHash()),
        hash.sha256(canonical(r, v)).equals(v.getContentHash()),
        link);
  }

  public AttachmentService.Download download(
      DoctorPrincipal actor, Long id, int number, Long attachmentId) {
    var r = allowed(actor, id, PatientAccessPolicy.Action.READ);
    var v = version(r, number);
    if (!v.getAttachmentIds().contains(attachmentId))
      throw new ApiException(404, "Tep khong thuoc phien ban");
    return attachments.download(r.getPatientId(), attachmentId);
  }

  private MedicalRecordResponse response(MedicalRecord r, RecordVersion v) {
    return new MedicalRecordResponse(
        r.getId(),
        r.getPatientId(),
        r.getCreatedBy(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        r.getCurrentVersion(),
        response(v));
  }

  private VersionResponse response(RecordVersion v) {
    return new VersionResponse(
        v.getId(),
        v.getVersionNumber(),
        v.getDoctorId(),
        v.getCreatedAt(),
        v.getPreviousVersionId(),
        v.getPreviousHash(),
        v.getExamDate(),
        v.getDiagnosis(),
        v.getPrescription(),
        v.getTestResults(),
        v.getNotes(),
        List.copyOf(v.getAttachmentIds()),
        v.getContentHash(),
        v.getCanonicalSchema(),
        v.getBlockchainStatus(),
        v.getBlockchainTransactionHash(),
        v.getBlockchainRecordId());
  }
}

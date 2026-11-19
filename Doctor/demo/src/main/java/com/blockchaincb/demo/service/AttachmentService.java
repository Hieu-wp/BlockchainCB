package com.blockchaincb.demo.service;

import com.blockchaincb.demo.entity.Attachment;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.repository.AttachmentRepository;
import com.blockchaincb.demo.security.*;
import com.blockchaincb.demo.storage.PrivateStorage;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachmentService {
  private final AttachmentRepository attachments;
  private final PrivateStorage storage;
  private final PatientAccessPolicy access;
  private final DoctorIdentityService identities;
  private final HashService hash;
  private final com.blockchaincb.demo.shared.SharedIntegrationSettings integration;

  public AttachmentService(
      AttachmentRepository attachments,
      PrivateStorage storage,
      PatientAccessPolicy access,
      DoctorIdentityService identities,
      HashService hash,
      com.blockchaincb.demo.shared.SharedIntegrationSettings integration) {
    this.attachments = attachments;
    this.storage = storage;
    this.access = access;
    this.identities = identities;
    this.hash = hash;
    this.integration = integration;
  }

  public record UploadResponse(Long id, String mediaType, long sizeBytes, String sha256) {}

  public record Download(byte[] content, String mediaType) {}

  @Transactional
  public UploadResponse upload(DoctorPrincipal actor, Long patientId, byte[] bytes) {
    identities.approved(actor);
    integration.requireLegacyRecords();
    access.require(actor.doctorId(), patientId, PatientAccessPolicy.Action.READ);
    try {
      access.require(actor.doctorId(), patientId, PatientAccessPolicy.Action.CREATE);
    } catch (ApiException e) {
      if (e.getStatus() != 403) throw e;
      access.require(actor.doctorId(), patientId, PatientAccessPolicy.Action.UPDATE);
    }
    if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024)
      throw new ApiException(400, "Kich thuoc tep khong hop le");
    String media = detect(bytes);
    var a = new Attachment();
    a.setPatientId(patientId);
    a.setUploadedBy(actor.doctorId());
    a.setStorageKey(storage.put(bytes));
    a.setMediaType(media);
    a.setSizeBytes(bytes.length);
    a.setSha256(hash.sha256(bytes));
    a.setCreatedAt(Instant.now());
    attachments.saveAndFlush(a);
    return new UploadResponse(a.getId(), media, bytes.length, a.getSha256());
  }

  private String detect(byte[] b) {
    if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-')
      return "application/pdf";
    if (b.length >= 8
        && Arrays.equals(Arrays.copyOf(b, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
      return "image/png";
    if (b.length >= 3 && b[0] == (byte) 255 && b[1] == (byte) 216 && b[2] == (byte) 255)
      return "image/jpeg";
    throw new ApiException(400, "Chi ho tro PDF, PNG, JPEG");
  }

  // Called inside record transaction, never expose arbitrary uploaded file IDs for download.
  public List<Attachment> forVersion(
      Long doctor, Long patient, List<Long> ids, Set<Long> inheritedIds) {
    if (new HashSet<>(ids).size() != ids.size()) throw new ApiException(400, "Tep bi lap");
    return ids.stream()
        .map(
            id -> {
              var a =
                  attachments
                      .findById(id)
                      .orElseThrow(() -> new ApiException(400, "Tep khong hop le"));
              if (!a.getPatientId().equals(patient)
                  || (!a.getUploadedBy().equals(doctor) && !inheritedIds.contains(id)))
                throw new ApiException(403, "Khong du quyen su dung tep");
              return a;
            })
        .toList();
  }

  public List<Attachment> descriptors(Long patient, List<Long> ids) {
    return ids.stream()
        .map(
            id -> {
              var a =
                  attachments
                      .findById(id)
                      .orElseThrow(() -> new ApiException(409, "Thieu tep cua phien ban"));
              if (!a.getPatientId().equals(patient))
                throw new ApiException(409, "Lien ket tep khong hop le");
              return a;
            })
        .toList();
  }

  public Download download(Long patient, Long attachmentId) {
    var a =
        attachments
            .findById(attachmentId)
            .orElseThrow(() -> new ApiException(404, "Khong tim thay tep"));
    if (!a.getPatientId().equals(patient)) throw new ApiException(403, "Khong du quyen");
    byte[] bytes = storage.read(a.getStorageKey());
    if (!hash.sha256(bytes).equals(a.getSha256()))
      throw new ApiException(409, "Tep khong con nguyen ven");
    return new Download(bytes, a.getMediaType());
  }
}

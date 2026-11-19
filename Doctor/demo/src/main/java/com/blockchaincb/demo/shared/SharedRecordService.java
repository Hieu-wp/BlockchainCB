package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.dto.SharedRecordResponse.*;
import com.blockchaincb.demo.security.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// PostgreSQL row locks need a regular transaction. Methods perform SELECT only;
// active grant rows remain locked until all clinical data has been read.
@Transactional
public class SharedRecordService {
  private final SharedRecordRepository records;
  private final SharedGrantRepository grants;
  private final SharedIntegrationSettings settings;
  private final DoctorIdentityService identities;

  public SharedRecordService(
      SharedRecordRepository records,
      SharedGrantRepository grants,
      SharedIntegrationSettings settings,
      DoctorIdentityService identities) {
    this.records = records;
    this.grants = grants;
    this.settings = settings;
    this.identities = identities;
  }

  private long guard(DoctorPrincipal actor) {
    identities.approved(actor);
    settings.requireEnabled();
    var contract = grants.contract();
    records.contract();
    return contract.doctorReferencesUser() ? actor.userId() : actor.doctorId();
  }

  public Page<Summary> history(DoctorPrincipal actor, String code, Long patient, Pageable page) {
    long doctor = guard(actor);
    if (patient != null) grants.requireReadLocked(patient, doctor);
    var result = records.history(doctor, actor.doctorId(), code, patient, page);
    // One current-consent check per patient, not per record. Stable lock order
    // also avoids inverse patient lock acquisition between concurrent history reads.
    result.getContent().stream().map(Summary::patientId).distinct().sorted()
        .forEach(patientId -> grants.requireReadLocked(patientId, doctor));
    return result;
  }

  private Summary allowed(DoctorPrincipal actor, long id) {
    long doctor = guard(actor);
    var record = records.allowed(id, doctor);
    grants.requireReadLocked(record.patientId(), doctor);
    return record;
  }

  public Detail detail(DoctorPrincipal actor, long id) {
    var r = allowed(actor, id);
    return new Detail(r, records.version(id, r.currentVersion()));
  }

  public Version version(DoctorPrincipal actor, long id, int number) {
    allowed(actor, id);
    return records.version(id, number);
  }

  public Page<VersionSummary> versions(DoctorPrincipal actor, long id, Pageable page) {
    allowed(actor, id);
    return records.versions(id, page);
  }
}

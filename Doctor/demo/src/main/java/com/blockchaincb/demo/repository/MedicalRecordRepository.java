package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.MedicalRecord;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
  @Query("select r from MedicalRecord r where r.patientId=:patient order by r.id desc")
  Page<MedicalRecord> forPatient(@Param("patient") Long patient, Pageable page);

  @Query(
      "select r from MedicalRecord r where r.createdBy=:doctor and (:patient is null or"
          + " r.patientId=:patient) and exists (select g.id from AccessGrant g where"
          + " g.patientId=r.patientId and g.doctorId=:doctor and g.canRead=true and g.revokedAt is"
          + " null and g.validFrom<=:now and (g.expiresAt is null or g.expiresAt>:now)) order by"
          + " r.id desc")
  Page<MedicalRecord> history(
      @Param("doctor") Long doctor,
      @Param("patient") Long patient,
      @Param("now") Instant now,
      Pageable page);
}

package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.Patient;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {
  @Query(
      "select p from Patient p, User u where p.userId=u.id and u.role='PATIENT' and lower(u.fullName) like"
          + " lower(concat('%', :q, '%')) and exists (select g.id from AccessGrant g where"
          + " g.patientId=p.id and g.doctorId=:doctor and g.canRead=true and g.revokedAt is null"
          + " and g.validFrom<=:now and (g.expiresAt is null or g.expiresAt>:now)) order by p.id")
  Page<Patient> visible(
      @Param("doctor") Long doctor, @Param("q") String q, @Param("now") Instant now, Pageable page);
}

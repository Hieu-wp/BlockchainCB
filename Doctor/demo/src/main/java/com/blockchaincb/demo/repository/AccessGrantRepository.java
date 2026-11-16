package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.AccessGrant;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, Long> {
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      "select g from AccessGrant g where g.patientId=:patient and g.doctorId=:doctor and"
          + " g.revokedAt is null and g.validFrom<=:now and (g.expiresAt is null or"
          + " g.expiresAt>:now)")
  List<AccessGrant> activeLocked(
      @Param("patient") Long patient, @Param("doctor") Long doctor, @Param("now") Instant now);
}

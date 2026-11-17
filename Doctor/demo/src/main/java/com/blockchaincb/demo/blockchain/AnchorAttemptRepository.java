package com.blockchaincb.demo.blockchain;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AnchorAttemptRepository extends JpaRepository<AnchorAttempt, String> {
  Optional<AnchorAttempt> findFirstByRecordIdAndVersionNumberOrderByCreatedAtDesc(
      Long record, int number);

  Optional<AnchorAttempt> findByTransactionHash(String hash);

  @Query(
      "select a from AnchorAttempt a where a.status in ('PREPARED','PENDING') and a.pollCount<100"
          + " and a.checkedAt<:before order by a.checkedAt")
  List<AnchorAttempt> due(
      @Param("before") Instant before, org.springframework.data.domain.Pageable page);
}

package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.RecordVersion;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface RecordVersionRepository extends JpaRepository<RecordVersion, Long> {
  Optional<RecordVersion> findByRecordIdAndVersionNumber(Long recordId, int versionNumber);

  Page<RecordVersion> findByRecordIdOrderByVersionNumberDesc(Long recordId, Pageable page);
}

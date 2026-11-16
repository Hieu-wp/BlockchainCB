package com.example.medical.repository;

import com.example.medical.entity.RecordVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecordVersionRepository extends JpaRepository<RecordVersion, Long> {

    Optional<RecordVersion> findByRecordIdAndVersionNo(Long recordId, int versionNo);
}

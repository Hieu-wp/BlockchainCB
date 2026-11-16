package com.example.medical.repository;

import com.example.medical.entity.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {
    Optional<VitalSign> findByRecordVersionId(Long versionId);
}
package com.example.medical.repository;

import com.example.medical.entity.RecordVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecordVersionRepository extends JpaRepository<RecordVersion, Long> {

    Optional<RecordVersion> findByRecordIdAndVersionNo(Long recordId, int versionNo);

    @Query("SELECT rv FROM RecordVersion rv, MedicalRecord r " +
            "WHERE rv.recordId = r.id AND r.patient.id = :patientId " +
            "ORDER BY rv.createdAt DESC")
    List<RecordVersion> findAllHistoryByPatientId(@Param("patientId") Long patientId);
}
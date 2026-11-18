package com.example.medical.repository;

import com.example.medical.entity.AccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, Long> {

    @Query("""
        SELECT g FROM AccessGrant g
        JOIN FETCH g.doctor d
        JOIN FETCH d.user u
        JOIN FETCH d.specialty s
        WHERE g.patient.id = :patientId
          AND g.status IN ('PENDING', 'ACTIVE')
          AND g.revoked = false
          AND d.status = 'ACTIVE'
        ORDER BY g.createdAt DESC
    """)
    List<AccessGrant> findPendingAndActiveByPatientId(@Param("patientId") Long patientId);
    List<AccessGrant> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    Optional<AccessGrant> findByIdAndPatientId(Long id, Long patientId);
}
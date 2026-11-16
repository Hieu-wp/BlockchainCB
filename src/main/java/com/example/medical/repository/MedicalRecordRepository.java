package com.example.medical.repository;

import com.example.medical.entity.MedicalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    // Dieu kien loc dung chung cho query va count query.
    // KHONG truyen null vao tham so (PostgreSQL bao loi "could not determine data type"),
    // nen service truyen gia tri mac dinh: khoang ngay rong rai, chuoi rong.
    String FILTER = """
            where r.patient.id = :patientId
              and r.visitAt >= :from
              and r.visitAt < :to
              and (:specialty = '' or s.code = :specialty)
              and (:keyword = ''
                   or lower(r.recordCode) like lower(concat('%', :keyword, '%'))
                   or lower(du.fullName)  like lower(concat('%', :keyword, '%')))
            """;

    @Query(value = """
            select r from MedicalRecord r
              join fetch r.doctor d
              join fetch d.user du
              join fetch r.specialty s
            """ + FILTER,
            countQuery = """
            select count(r) from MedicalRecord r
              join r.doctor d
              join d.user du
              join r.specialty s
            """ + FILTER)
    Page<MedicalRecord> search(@Param("patientId") Long patientId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("specialty") String specialty,
                               @Param("keyword") String keyword,
                               Pageable pageable);

    @Query("""
            select r from MedicalRecord r
              join fetch r.doctor d
              join fetch d.user
              join fetch r.specialty
              join fetch r.patient
            where r.id = :id
            """)
    Optional<MedicalRecord> findDetailById(@Param("id") Long id);
}

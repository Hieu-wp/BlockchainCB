package com.example.medical.entity;

import com.example.medical.entity.Enums.ChainStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 - Moi lan bac si tao/sua ho so la mot dong moi (khong UPDATE de len),
 - de sau nay khop voi co che version tren smart contract.
 */
@Entity
@Table(name = "record_versions")
@Getter
@Setter
@NoArgsConstructor
public class RecordVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "room_name", length = 50)
    private String roomName;

    @Column(name = "visit_type", length = 100)
    private String visitType;

    @Column(columnDefinition = "text")
    private String diagnosis;

    @Column(name = "secondary_diagnosis", columnDefinition = "text")
    private String secondaryDiagnosis;

    @Column(name = "treatment_direction")
    private String treatmentDirection;

    @Column(columnDefinition = "text")
    private String prescription;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "next_visit_date")
    private LocalDate nextVisitDate;

    @Column(name = "next_visit_note", columnDefinition = "text")
    private String nextVisitNote;

    @Column(name = "data_hash")
    private String dataHash;

    @Column(name = "tx_hash")
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "chain_status", nullable = false)
    private ChainStatus chainStatus = ChainStatus.NONE;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
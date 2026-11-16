package com.example.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescription_items")
@Getter @Setter @NoArgsConstructor
public class PrescriptionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_version_id", nullable = false)
    private RecordVersion recordVersion;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "medicine_name", nullable = false)
    private String medicineName;

    @Column(name = "active_ingredient")
    private String activeIngredient;

    @Column(name = "dosage_instruction")
    private String dosageInstruction;

    @Column(name = "quantity_text", nullable = false)
    private String quantityText;
}

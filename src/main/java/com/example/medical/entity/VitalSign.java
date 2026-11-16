package com.example.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "vital_signs")
@Getter @Setter @NoArgsConstructor
public class VitalSign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_version_id", nullable = false, unique = true)
    private RecordVersion recordVersion;

    @Column(name = "blood_pressure")
    private String bloodPressure;

    @Column(name = "heart_rate")
    private Integer heartRate;

    private BigDecimal temperature;
    private Integer spo2;
    private BigDecimal weight;
    private BigDecimal height;
    private BigDecimal bmi;
}
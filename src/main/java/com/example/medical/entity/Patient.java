package com.example.medical.entity;

import com.example.medical.entity.Enums.FollowStatus;
import com.example.medical.entity.Enums.Gender;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "patient_code", nullable = false, unique = true)
    private String patientCode;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(name = "blood_type")
    private String bloodType;

    private String phone;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "follow_status", nullable = false)
    private FollowStatus followStatus = FollowStatus.FOLLOWING;
}

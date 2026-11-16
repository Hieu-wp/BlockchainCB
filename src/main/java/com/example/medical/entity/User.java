package com.example.medical.entity;

import com.example.medical.entity.Enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Dung o giai doan blockchain, hien tai de trong. */
    @Column(name = "wallet_address")
    private String walletAddress;

    @Column(nullable = false)
    private boolean enabled = true;

    // Cot nay co DEFAULT now() trong DB, nen chi doc, khong ghi
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}

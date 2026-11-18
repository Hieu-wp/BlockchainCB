package com.example.medical.dto;

import java.time.LocalDateTime;

public record AccessGrantDto(
        Long id,
        Long doctorId,
        String doctorName,
        String doctorWallet,
        String specialtyName,
        String hospitalName,
        String status,
        String txHash,
        LocalDateTime createdAt,
        LocalDateTime respondedAt,
        LocalDateTime expiresAt
) {}
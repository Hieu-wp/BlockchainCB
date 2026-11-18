package com.example.medical.dto;

import java.time.LocalDateTime;

public record AccessLogItemDto(
        String id,
        LocalDateTime timestamp,
        String category,
        String actionType,
        String actionLabel,
        String doctorName,
        String specialtyName,
        String doctorWallet,
        String recordCode,
        String description,
        String txHash,
        String dataHash,
        String chainStatus
) {}
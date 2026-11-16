package com.example.medical.dto;

import com.example.medical.entity.Enums.RecordStatus;

import java.time.LocalDateTime;

/** Mot dong trong bang "Danh sach ho so". */
public record RecordListItem(
        Long id,
        String recordCode,
        LocalDateTime visitAt,
        String specialtyCode,
        String specialtyName,
        String doctorName,
        String reason,
        RecordStatus status) {
}

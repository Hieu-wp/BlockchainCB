package com.example.medical.service;

import com.example.medical.dto.AccessGrantDto;
import com.example.medical.entity.AccessGrant;
import com.example.medical.entity.AccessGrant.GrantStatus;
import com.example.medical.entity.Patient;
import com.example.medical.repository.AccessGrantRepository;
import com.example.medical.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccessGrantService {

    private final AccessGrantRepository grantRepository;
    private final PatientRepository patientRepository;

    private Patient currentPatient(Long userId) {
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ bệnh nhân"));
    }

    @Transactional(readOnly = true)
    public List<AccessGrantDto> listForPatient(Long userId) {
        Patient patient = currentPatient(userId);
        return grantRepository.findPendingAndActiveByPatientId(patient.getId())
                .stream()
                .map(g -> new AccessGrantDto(
                        g.getId(),
                        g.getDoctor().getId(),
                        g.getDoctor().getFullName(), // lấy trực tiếp từ cột full_name của bảng doctors
                        g.getDoctor().getUser().getWalletAddress(),
                        g.getDoctor().getSpecialty().getName(),
                        g.getDoctor().getHospital(), // lấy trực tiếp từ cột hospital của bảng doctors
                        g.getStatus().name(),
                        g.getTxHash(),
                        g.getCreatedAt(),
                        g.getRespondedAt(),
                        g.getExpiresAt()
                ))
                .toList();
    }

    @Transactional
    public void updateGrantStatus(Long userId, Long grantId, GrantStatus newStatus, String txHash) {
        Patient patient = currentPatient(userId);
        AccessGrant grant = grantRepository.findByIdAndPatientId(grantId, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu phân quyền"));

        grant.setStatus(newStatus);
        grant.setRespondedAt(LocalDateTime.now());

        if (newStatus == GrantStatus.ACTIVE) {
            grant.setRevoked(false);
            // Mặc định cấp quyền trong 1 năm (365 ngày)
            grant.setExpiresAt(LocalDateTime.now().plusDays(365));
        } else if (newStatus == GrantStatus.REVOKED) {
            grant.setRevoked(true);
        }

        if (txHash != null && !txHash.isBlank()) {
            grant.setTxHash(txHash.trim());
        }
        grantRepository.save(grant);
    }
}

package com.example.medical.controller;

import com.example.medical.dto.AccessLogItemDto;
import com.example.medical.entity.*;
import com.example.medical.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/patient/access-logs")
@RequiredArgsConstructor
public class PatientAccessLogController {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final RecordVersionRepository recordVersionRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AccessLogItemDto>> getAccessLogs(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));
        Patient patient = patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh nhân"));

        List<AccessLogItemDto> logs = new ArrayList<>();

        // 1. Lấy lịch sử biến động phân quyền từ bảng access_grants
        List<AccessGrant> grants = accessGrantRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId());
        for (AccessGrant g : grants) {
            Doctor doc = g.getDoctor();
            String docName = doc != null ? doc.getFullName() : "Bác sĩ";
            String specName = (doc != null && doc.getSpecialty() != null) ? doc.getSpecialty().getName() : "—";
            String wallet = (doc != null && doc.getUser() != null) ? doc.getUser().getWalletAddress() : null;

            logs.add(new AccessLogItemDto(
                    "GRANT-REQ-" + g.getId(),
                    g.getCreatedAt(),
                    "GRANT",
                    "GRANT_REQUEST",
                    "Yêu cầu cấp quyền",
                    docName,
                    specName,
                    wallet,
                    null,
                    "Bác sĩ gửi yêu cầu xin quyền nhập và cập nhật hồ sơ sức khỏe.",
                    null,
                    null,
                    "NONE"
            ));

            String statusStr = g.getStatus() != null ? g.getStatus().name() : "";
            if (g.getRespondedAt() != null && !"PENDING".equalsIgnoreCase(statusStr)) {
                String actionType = "GRANT_" + statusStr.toUpperCase();
                String actionLabel;
                String desc;

                switch (statusStr.toUpperCase()) {
                    case "ACTIVE" -> {
                        actionLabel = "Đã cấp quyền truy cập";
                        desc = "Bệnh nhân chấp nhận yêu cầu và cấp quyền trên Smart Contract.";
                    }
                    case "REVOKED" -> {
                        actionLabel = "Đã thu hồi quyền";
                        desc = "Bệnh nhân đã thu hồi quyền truy cập hồ sơ của bác sĩ.";
                    }
                    case "REJECTED" -> {
                        actionLabel = "Từ chối yêu cầu";
                        desc = "Bệnh nhân từ chối yêu cầu xin quyền truy cập.";
                    }
                    default -> {
                        actionLabel = statusStr;
                        desc = "Cập nhật trạng thái phân quyền.";
                    }
                }

                logs.add(new AccessLogItemDto(
                        "GRANT-RES-" + g.getId(),
                        g.getRespondedAt(),
                        "GRANT",
                        actionType,
                        actionLabel,
                        docName,
                        specName,
                        wallet,
                        null,
                        desc,
                        g.getTxHash(),
                        null,
                        g.getTxHash() != null ? "CONFIRMED" : "NONE"
                ));
            }
        }

        // 2. Lấy lịch sử tạo mới & cập nhật hồ sơ từ bảng record_versions
        List<RecordVersion> versions = recordVersionRepository.findAllHistoryByPatientId(patient.getId());
        for (RecordVersion rv : versions) {
            MedicalRecord rec = medicalRecordRepository.findById(rv.getRecordId()).orElse(null);
            if (rec == null) continue;

            Doctor doc = rec.getDoctor();
            String docName = doc != null ? doc.getFullName() : "Bác sĩ";
            String specName = rec.getSpecialty() != null ? rec.getSpecialty().getName() : "—";
            String wallet = (doc != null && doc.getUser() != null) ? doc.getUser().getWalletAddress() : null;

            boolean isFirstVersion = rv.getVersionNo() == 1;
            String actionType = isFirstVersion ? "RECORD_CREATED" : "RECORD_UPDATED";
            String actionLabel = isFirstVersion ? "Tạo hồ sơ khám mới" : "Cập nhật bệnh án (v" + rv.getVersionNo() + ")";
            String desc = isFirstVersion
                    ? "Khởi tạo bệnh án điện tử phiên bản v1 · Chẩn đoán: " + (rv.getDiagnosis() != null ? rv.getDiagnosis() : "Đang cập nhật")
                    : "Bổ sung thông tin bệnh án phiên bản v" + rv.getVersionNo() + (rv.getDiagnosis() != null ? " · " + rv.getDiagnosis() : "");

            String chainStatusStr = rv.getChainStatus() != null ? rv.getChainStatus().name() : "NONE";

            logs.add(new AccessLogItemDto(
                    "VER-" + rv.getId(),
                    rv.getCreatedAt(),
                    "RECORD",
                    actionType,
                    actionLabel,
                    docName,
                    specName,
                    wallet,
                    rec.getRecordCode(),
                    desc,
                    rv.getTxHash(),
                    rv.getDataHash(),
                    chainStatusStr
            ));
        }

        logs.sort(Comparator.comparing(
                AccessLogItemDto::timestamp,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return ResponseEntity.ok(logs);
    }
}
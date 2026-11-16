package com.example.medical.service;

import com.example.medical.dto.*;
import com.example.medical.entity.MedicalRecord;
import com.example.medical.entity.Patient;
import com.example.medical.entity.RecordVersion;
import com.example.medical.repository.MedicalRecordRepository;
import com.example.medical.repository.PatientRepository;
import com.example.medical.repository.PrescriptionItemRepository;
import com.example.medical.repository.RecordAttachmentRepository;
import com.example.medical.repository.RecordVersionRepository;
import com.example.medical.repository.VitalSignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientRecordService {

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(3000, 1, 1, 0, 0);

    private final PatientRepository patientRepository;
    private final MedicalRecordRepository recordRepository;
    private final RecordVersionRepository versionRepository;
    private final VitalSignRepository vitalSignRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final RecordAttachmentRepository recordAttachmentRepository;

    private Patient currentPatient(Long userId) {
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ bệnh nhân của tài khoản này"));
    }

    /** Header thong tin benh nhan. */
    public PatientProfile getProfile(Long userId) {
        Patient p = currentPatient(userId);
        int age = Period.between(p.getDateOfBirth(), LocalDate.now()).getYears();
        return new PatientProfile(
                p.getUser().getFullName(),
                p.getPatientCode(),
                p.getDateOfBirth(),
                age,
                p.getGender().name(),
                p.getBloodType(),
                p.getPhone(),
                p.getAddress(),
                p.getFollowStatus().name(),
                p.getUser().getWalletAddress());
    }

    /** Bang danh sach ho so: loc theo ngay kham, chuyen khoa, tu khoa; co phan trang. */
    public PageResponse<RecordListItem> list(Long userId, LocalDate date, String specialty,
                                             String keyword, int page, int size) {
        Patient patient = currentPatient(userId);

        // Khong truyen null vao query (PostgreSQL), dung gia tri mac dinh
        LocalDateTime from = date != null ? date.atStartOfDay() : MIN_DATE;
        LocalDateTime to = date != null ? date.plusDays(1).atStartOfDay() : MAX_DATE;
        String sp = specialty == null ? "" : specialty.trim();
        String kw = keyword == null ? "" : keyword.trim();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);   // chan size qua lon
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "visitAt"));

        return PageResponse.of(
                recordRepository.search(patient.getId(), from, to, sp, kw, pageable)
                        .map(r -> new RecordListItem(
                                r.getId(),
                                r.getRecordCode(),
                                r.getVisitAt(),
                                r.getSpecialty().getCode(),
                                r.getSpecialty().getName(),
                                r.getDoctor().getUser().getFullName(),
                                r.getReason(),
                                r.getStatus())));
    }

    /** Nut "Xem": chi chu ho so moi duoc xem. */
    public RecordDetail detail(Long userId, Long recordId) {
        Patient patient = currentPatient(userId);
        int age = Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears();

        MedicalRecord r = recordRepository.findDetailById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ"));

        if (!r.getPatient().getId().equals(patient.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ");
        }

        RecordVersion v = versionRepository
                .findByRecordIdAndVersionNo(r.getId(), r.getCurrentVersion())
                .orElse(null);

        RecordDetail.VitalDto vitalDto = null;
        List<RecordDetail.PrescriptionItemDto> rxList = List.of();
        List<RecordDetail.AttachmentDto> fileList = List.of();

        if (v != null) {
            vitalDto = vitalSignRepository.findByRecordVersionId(v.getId())
                    .map(vs -> new RecordDetail.VitalDto(
                            vs.getBloodPressure(), vs.getHeartRate(), vs.getTemperature(),
                            vs.getSpo2(), vs.getWeight(), vs.getHeight(), vs.getBmi()))
                    .orElse(null);

            rxList = prescriptionItemRepository.findByRecordVersionIdOrderBySortOrderAsc(v.getId())
                    .stream()
                    .map(item -> new RecordDetail.PrescriptionItemDto(
                            item.getSortOrder(), item.getMedicineName(), item.getActiveIngredient(),
                            item.getDosageInstruction(), item.getQuantityText()))
                    .toList();

            fileList = recordAttachmentRepository.findByRecordVersionId(v.getId())
                    .stream()
                    .map(att -> new RecordDetail.AttachmentDto(
                            att.getId(), att.getFileName(), att.getFileType(),
                            att.getFileSizeLabel(), att.getFileUrl()))
                    .toList();
        }

        return new RecordDetail(
                r.getId(), r.getRecordCode(), r.getVisitAt(),
                r.getSpecialty().getName(), r.getDoctor().getUser().getFullName(),
                r.getReason(), r.getStatus().name(), r.getCurrentVersion(),
                patient.getUser().getFullName(), patient.getPatientCode(),
                patient.getDateOfBirth(), age, patient.getGender().name(), patient.getBloodType(),
                v != null ? v.getRoomName() : null,
                v != null ? v.getVisitType() : null,
                v != null ? v.getDiagnosis() : null,
                v != null ? v.getSecondaryDiagnosis() : null,
                v != null ? v.getTreatmentDirection() : null,
                v != null ? v.getPrescription() : null,
                v != null ? v.getNote() : null,
                v != null ? v.getNextVisitDate() : null,
                v != null ? v.getNextVisitNote() : null,
                vitalDto, rxList, fileList,
                v != null ? v.getDataHash() : null,
                v != null ? v.getChainStatus().name() : null
        );
    }
}
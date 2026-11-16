package com.example.medical.repository;

import com.example.medical.entity.RecordAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordAttachmentRepository extends JpaRepository<RecordAttachment, Long> {
    List<RecordAttachment> findByRecordVersionId(Long versionId);
}
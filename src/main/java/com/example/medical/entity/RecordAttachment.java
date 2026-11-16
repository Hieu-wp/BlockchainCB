package com.example.medical.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "record_attachments")
@Getter @Setter @NoArgsConstructor
public class RecordAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_version_id", nullable = false)
    private RecordVersion recordVersion;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "file_size_label")
    private String fileSizeLabel;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;
}
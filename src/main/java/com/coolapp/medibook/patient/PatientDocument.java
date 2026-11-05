package com.coolapp.medibook.patient;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_document")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class PatientDocument extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "file_key", nullable = false)
    private String fileKey;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocumentType docType;
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}

package com.coolapp.medibook.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, UUID> {
    List<PatientDocument> findByPatientIdOrderByUploadedAtDesc(UUID patientId);
}

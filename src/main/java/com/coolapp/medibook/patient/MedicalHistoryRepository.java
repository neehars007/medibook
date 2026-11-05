package com.coolapp.medibook.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, UUID> {
    List<MedicalHistory> findByPatientIdOrderByTypeAscCreatedAtDesc(UUID patientId);

    List<MedicalHistory> findByPatientIdInAndActiveTrueAndTypeIn(Collection<UUID> patientIds, Collection<MedicalHistoryType> types);
}

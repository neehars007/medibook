package com.coolapp.medibook.consultation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitNoteRepository extends JpaRepository<VisitNote, UUID> {
    Optional<VisitNote> findByAppointmentId(UUID appointmentId);

    List<VisitNote> findByPatientIdAndFinalizedAtIsNotNullOrderByFinalizedAtDesc(UUID patientId);

    List<VisitNote> findByAppointmentIdInAndFinalizedAtIsNotNull(Collection<UUID> appointmentIds);
}

package com.coolapp.medibook.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntakeFormRepository extends JpaRepository<IntakeForm, UUID> {
    Optional<IntakeForm> findByAppointmentId(UUID appointmentId);

    List<IntakeForm> findByAppointmentIdIn(Collection<UUID> appointmentIds);
}

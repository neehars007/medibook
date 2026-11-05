package com.coolapp.medibook.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentTypeRepository extends JpaRepository<AppointmentType, UUID> {
    List<AppointmentType> findBySpecialtyIdAndActiveTrueOrderByNameAsc(UUID specialtyId);

    List<AppointmentType> findByActiveTrueOrderByNameAsc();
}

package com.coolapp.medibook.clinic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.Optional;
import java.util.UUID;

public interface ClinicHoursRepository extends JpaRepository<ClinicHours, UUID> {
    Optional<ClinicHours> findByDayOfWeek(DayOfWeek day);
}

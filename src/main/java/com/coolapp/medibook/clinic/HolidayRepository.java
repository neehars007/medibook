package com.coolapp.medibook.clinic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {
    boolean existsByDate(LocalDate date);

    List<Holiday> findByDateGreaterThanEqualOrderByDateAsc(LocalDate from);
}

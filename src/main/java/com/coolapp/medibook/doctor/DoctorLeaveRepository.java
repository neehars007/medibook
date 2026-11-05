package com.coolapp.medibook.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, UUID> {
    @Query("select l from DoctorLeave l where l.doctorId = :doctorId and l.startAt < :to and l.endAt > :from order by l.startAt")
    List<DoctorLeave> findOverlapping(@Param("doctorId") UUID doctorId, @Param("from") Instant from, @Param("to") Instant to);

    List<DoctorLeave> findByDoctorIdAndEndAtAfterOrderByStartAtAsc(UUID doctorId, Instant after);
}

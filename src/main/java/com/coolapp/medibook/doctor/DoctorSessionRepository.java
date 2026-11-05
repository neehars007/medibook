package com.coolapp.medibook.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface DoctorSessionRepository extends JpaRepository<DoctorSession, UUID> {
    List<DoctorSession> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(UUID doctorId);

    List<DoctorSession> findByDoctorIdAndDayOfWeek(UUID doctorId, DayOfWeek day);

    List<DoctorSession> findByRoomIdAndDayOfWeek(UUID roomId, DayOfWeek day);
}

package com.coolapp.medibook.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    @Query("""
            select a from Appointment a
            where a.doctorId = :doctorId and a.status in :statuses
              and a.startAt < :to and a.endAt > :from
            order by a.startAt""")
    List<Appointment> findForDoctorOverlapping(@Param("doctorId") UUID doctorId,
                                               @Param("statuses") Collection<AppointmentStatus> statuses,
                                               @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select a from Appointment a
            where a.roomId in :roomIds and a.status in :statuses
              and a.startAt < :to and a.endAt > :from""")
    List<Appointment> findForRoomsOverlapping(@Param("roomIds") Collection<UUID> roomIds,
                                              @Param("statuses") Collection<AppointmentStatus> statuses,
                                              @Param("from") Instant from, @Param("to") Instant to);

    /** "Treating relationship": an active appointment, or a completed one since {@code since}. */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.doctorId = :doctorId and a.patientId = :patientId
              and (a.status in :active or (a.status = com.coolapp.medibook.appointment.AppointmentStatus.COMPLETED and a.startAt >= :since))""")
    boolean hasTreatingRelationship(@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId,
                                    @Param("active") Collection<AppointmentStatus> active, @Param("since") Instant since);

    @Query("""
            select max(a.startAt) from Appointment a
            where a.doctorId = :doctorId and a.patientId = :patientId
              and a.status = com.coolapp.medibook.appointment.AppointmentStatus.COMPLETED""")
    Instant lastCompletedVisit(@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId);

    long countByPatientIdAndStatus(UUID patientId, AppointmentStatus status);

    @Query("""
            select a from Appointment a
            where a.patientId in :patientIds and a.status in :active and a.endAt > :now
            order by a.startAt asc""")
    List<Appointment> findUpcoming(@Param("patientIds") Collection<UUID> patientIds,
                                   @Param("active") Collection<AppointmentStatus> active, @Param("now") Instant now);

    @Query("""
            select a from Appointment a
            where a.patientId in :patientIds and (a.status not in :active or a.endAt <= :now)
            order by a.startAt desc""")
    List<Appointment> findPast(@Param("patientIds") Collection<UUID> patientIds,
                               @Param("active") Collection<AppointmentStatus> active, @Param("now") Instant now);

    List<Appointment> findByDoctorIdAndStartAtBetweenOrderByStartAtAsc(UUID doctorId, Instant from, Instant to);

    List<Appointment> findByStartAtBetweenOrderByDoctorIdAscStartAtAsc(Instant from, Instant to);

    List<Appointment> findByDoctorIdAndStatusAndStartAtBetweenOrderByTokenNumberAsc(UUID doctorId, AppointmentStatus status,
                                                                                    Instant from, Instant to);

    List<Appointment> findByStatusInAndStartAtBetween(Collection<AppointmentStatus> statuses, Instant from, Instant to);

    List<Appointment> findByDoctorIdAndStatusInAndStartAtBetween(UUID doctorId, Collection<AppointmentStatus> statuses,
                                                                 Instant from, Instant to);

    List<Appointment> findByStatusAndCreatedAtBefore(AppointmentStatus status, Instant before);

    List<Appointment> findByPatientIdOrderByStartAtDesc(UUID patientId);

    @Query(value = """
            select avg(extract(epoch from (consult_ended_at - consult_started_at)) / 60.0)
            from appointment
            where doctor_id = :doctorId and status = 'COMPLETED'
              and consult_started_at is not null and consult_ended_at is not null
              and start_at >= :since""", nativeQuery = true)
    Double averageConsultMinutes(@Param("doctorId") UUID doctorId, @Param("since") Instant since);
}

package com.coolapp.medibook.queue;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentDtos.AppointmentDto;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentViews;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.room.Room;
import com.coolapp.medibook.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final int DEFAULT_CONSULT_MINUTES = 15;

    private final QueueTokenSequenceRepository sequences;
    private final AppointmentRepository appointments;
    private final AppointmentViews views;
    private final DoctorRepository doctors;
    private final RoomRepository rooms;
    private final ClinicService clinicService;
    private final AuditService audit;
    private final Clock clock;

    /** Atomic per-doctor/day token: two receptionists can never issue the same number. */
    @Transactional
    public int nextToken(UUID doctorId, LocalDate day) {
        sequences.ensureRow(doctorId, day);
        QueueTokenSequence seq = sequences.lockById(new QueueTokenKey(doctorId, day)).orElseThrow();
        seq.setLastValue(seq.getLastValue() + 1);
        return seq.getLastValue();
    }

    public record QueueEntry(int token, String status, Instant checkedInAt, int estimatedWaitMinutes) {}

    public record DoctorQueue(UUID doctorId, String doctorName, String roomName, Integer nowServing,
                              List<QueueEntry> waiting, int averageConsultMinutes) {}

    /** Anonymized for the waiting-room TV: tokens, doctor and room only - never patient names. */
    @Transactional(readOnly = true)
    public List<DoctorQueue> display() {
        ZoneId zone = clinicService.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<DoctorQueue> out = new ArrayList<>();
        for (Doctor d : doctors.findByActiveTrue()) {
            DoctorQueue q = queueFor(d, from, to);
            if (q.nowServing() != null || !q.waiting().isEmpty()) {
                out.add(q);
            }
        }
        out.sort(Comparator.comparing(DoctorQueue::doctorName));
        return out;
    }

    @Transactional(readOnly = true)
    public DoctorQueue forDoctor(UUID doctorId) {
        ZoneId zone = clinicService.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Doctor d = doctors.findById(doctorId).orElseThrow(() -> ApiException.notFound("Doctor"));
        return queueFor(d, today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** Doctor clicks "Call next": lowest waiting token goes into consultation. */
    @Transactional
    public AppointmentDto callNext() {
        Doctor me = doctors.findByUserId(CurrentUser.get().id()).orElseThrow(() -> ApiException.forbidden("Doctor profile required"));
        ZoneId zone = clinicService.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        if (!appointments.findByDoctorIdAndStatusAndStartAtBetweenOrderByTokenNumberAsc(me.getId(),
                AppointmentStatus.IN_CONSULTATION, from, to).isEmpty()) {
            throw ApiException.conflict("Finalize the current visit before calling the next patient");
        }
        Appointment next = appointments.findByDoctorIdAndStatusAndStartAtBetweenOrderByTokenNumberAsc(me.getId(),
                        AppointmentStatus.CHECKED_IN, from, to).stream().findFirst()
                .orElseThrow(() -> ApiException.conflict("No patients waiting"));
        next.transitionTo(AppointmentStatus.IN_CONSULTATION);
        next.setConsultStartedAt(clock.instant());
        audit.log("QUEUE_CALL_NEXT", "Appointment", next.getId(), "token " + next.getTokenNumber());
        return views.one(next);
    }

    public int averageConsultMinutes(UUID doctorId) {
        Double avg = appointments.averageConsultMinutes(doctorId, clock.instant().minus(Duration.ofDays(30)));
        return avg == null ? DEFAULT_CONSULT_MINUTES : Math.max(1, (int) Math.round(avg));
    }

    private DoctorQueue queueFor(Doctor d, Instant from, Instant to) {
        List<Appointment> serving = appointments.findByDoctorIdAndStatusAndStartAtBetweenOrderByTokenNumberAsc(
                d.getId(), AppointmentStatus.IN_CONSULTATION, from, to);
        List<Appointment> waiting = appointments.findByDoctorIdAndStatusAndStartAtBetweenOrderByTokenNumberAsc(
                d.getId(), AppointmentStatus.CHECKED_IN, from, to);
        int avg = averageConsultMinutes(d.getId());
        List<QueueEntry> entries = new ArrayList<>();
        int ahead = serving.isEmpty() ? 0 : 1;
        for (Appointment a : waiting) {
            entries.add(new QueueEntry(a.getTokenNumber() == null ? 0 : a.getTokenNumber(), "WAITING", a.getCheckedInAt(), ahead * avg));
            ahead++;
        }
        UUID roomId = !serving.isEmpty() ? serving.get(0).getRoomId() : waiting.isEmpty() ? null : waiting.get(0).getRoomId();
        String roomName = roomId == null ? null : rooms.findById(roomId).map(Room::getName).orElse(null);
        return new DoctorQueue(d.getId(), d.getFullName(), roomName,
                serving.isEmpty() ? null : serving.get(0).getTokenNumber(), entries, avg);
    }
}

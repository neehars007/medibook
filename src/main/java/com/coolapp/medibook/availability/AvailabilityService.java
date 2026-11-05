package com.coolapp.medibook.availability;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentType;
import com.coolapp.medibook.appointment.AppointmentTypeRepository;
import com.coolapp.medibook.appointment.Hold;
import com.coolapp.medibook.appointment.HoldService;
import com.coolapp.medibook.clinic.Clinic;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorLeaveRepository;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.doctor.DoctorSession;
import com.coolapp.medibook.doctor.DoctorSessionRepository;
import com.coolapp.medibook.room.Room;
import com.coolapp.medibook.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Loads everything the {@link SlotCalculator} needs from the DB and Redis. */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final ClinicService clinicService;
    private final DoctorRepository doctors;
    private final DoctorSessionRepository sessions;
    private final DoctorLeaveRepository leaves;
    private final AppointmentRepository appointments;
    private final AppointmentTypeRepository types;
    private final RoomRepository rooms;
    private final HoldService holds;
    private final Clock clock;

    public record Options(ConsultationMode mode, UUID patientId, UUID excludeHoldId, boolean ignoreNotice) {
        public static Options of(ConsultationMode mode, UUID patientId) {
            return new Options(mode, patientId, null, false);
        }
    }

    @Transactional(readOnly = true)
    public List<Slot> forDoctor(UUID doctorId, UUID typeId, LocalDate date, Options opt) {
        Doctor doctor = doctors.findById(doctorId).filter(Doctor::isActive)
                .orElseThrow(() -> ApiException.notFound("Doctor"));
        AppointmentType type = types.findById(typeId).filter(AppointmentType::isActive)
                .orElseThrow(() -> ApiException.notFound("Appointment type"));
        if (!type.getSpecialtyId().equals(doctor.getSpecialtyId())) {
            throw ApiException.badRequest("Appointment type does not belong to this doctor's specialty");
        }
        return compute(doctor, type, date, opt);
    }

    /** "Any doctor in Dermatology": merges slots across the specialty's doctors. */
    @Transactional(readOnly = true)
    public List<Slot> forSpecialty(UUID specialtyId, UUID typeId, LocalDate date, Options opt) {
        AppointmentType type = types.findById(typeId).filter(AppointmentType::isActive)
                .orElseThrow(() -> ApiException.notFound("Appointment type"));
        if (!type.getSpecialtyId().equals(specialtyId)) {
            throw ApiException.badRequest("Appointment type does not belong to this specialty");
        }
        List<Slot> all = new ArrayList<>();
        for (Doctor d : doctors.findBySpecialtyIdAndActiveTrue(specialtyId)) {
            all.addAll(compute(d, type, date, opt));
        }
        all.sort(Comparator.comparing(Slot::startAt));
        return all;
    }

    /** First free slot in the next {@code days} days (doctor cards). */
    @Transactional(readOnly = true)
    public Optional<Slot> nextAvailable(Doctor doctor, AppointmentType type, int days) {
        LocalDate today = LocalDate.now(clock.withZone(clinicService.zone()));
        for (int i = 0; i < days; i++) {
            List<Slot> slots = compute(doctor, type, today.plusDays(i), Options.of(null, null));
            if (!slots.isEmpty()) {
                return Optional.of(slots.get(0));
            }
        }
        return Optional.empty();
    }

    private List<Slot> compute(Doctor doctor, AppointmentType type, LocalDate date, Options opt) {
        Clinic clinic = clinicService.get();
        ZoneId zone = clinic.zone();
        Instant now = clock.instant();

        if (type.isFollowUp()) {
            if (opt.patientId() == null) {
                return List.of();
            }
            Instant last = appointments.lastCompletedVisit(doctor.getId(), opt.patientId());
            int window = type.getFollowUpWindowDays() == null ? 14 : type.getFollowUpWindowDays();
            if (!FollowUpPolicy.isEligible(last, now, window)) {
                return List.of();
            }
        }
        ConsultationMode mode = opt.mode();
        if (mode != null && !type.supports(mode)) {
            return List.of();
        }
        if (mode == null && type.getMode() != null) {
            mode = type.getMode();
        }

        Optional<LocalTime[]> open = clinicService.openWindow(date);
        if (open.isEmpty()) {
            return List.of();
        }

        List<DoctorSession> daySessions = sessions.findByDoctorIdAndDayOfWeek(doctor.getId(), date.getDayOfWeek());
        if (daySessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, Room> roomById = rooms.findAll().stream().filter(Room::isActive)
                .collect(Collectors.toMap(Room::getId, r -> r));

        List<SlotCalculator.SessionWindow> windows = daySessions.stream()
                .filter(s -> s.getMode() == ConsultationMode.VIDEO || s.getRoomId() == null || roomById.containsKey(s.getRoomId()))
                .map(s -> new SlotCalculator.SessionWindow(s.getStartTime(), s.getEndTime(), s.getRoomId(),
                        s.getRoomId() == null ? null : roomById.get(s.getRoomId()).getType(), s.getMode()))
                .toList();

        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        Duration buffer = Duration.ofMinutes(clinic.getBufferMinutes());

        // Doctor: active appointments + holds (buffered), leave (not buffered)
        List<Interval> doctorBusy = new ArrayList<>(appointments
                .findForDoctorOverlapping(doctor.getId(), AppointmentStatus.ACTIVE, dayStart.minus(buffer), dayEnd.plus(buffer))
                .stream().map(a -> new Interval(a.getStartAt(), a.getEndAt())).toList());
        holds.activeForDoctor(doctor.getId(), date).stream()
                .filter(h -> !h.id().equals(opt.excludeHoldId()))
                .forEach(h -> doctorBusy.add(new Interval(h.startAt(), h.endAt())));
        List<Interval> blocked = leaves.findOverlapping(doctor.getId(), dayStart, dayEnd).stream()
                .map(l -> new Interval(l.getStartAt(), l.getEndAt())).toList();

        // Rooms that may be needed
        List<UUID> requiredRooms = type.getRequiresRoomType() == null ? List.of()
                : rooms.findByTypeAndActiveTrue(type.getRequiresRoomType()).stream().map(Room::getId).toList();
        Set<UUID> relevantRooms = new HashSet<>(requiredRooms);
        windows.stream().map(SlotCalculator.SessionWindow::roomId).filter(Objects::nonNull).forEach(relevantRooms::add);

        Map<UUID, List<Interval>> roomBusy = new HashMap<>();
        if (!relevantRooms.isEmpty()) {
            for (Appointment a : appointments.findForRoomsOverlapping(relevantRooms, AppointmentStatus.ACTIVE,
                    dayStart.minus(buffer), dayEnd.plus(buffer))) {
                // the doctor's own appointments are already in doctorBusy; still block the room for others
                roomBusy.computeIfAbsent(a.getRoomId(), k -> new ArrayList<>()).add(new Interval(a.getStartAt(), a.getEndAt()));
            }
            for (UUID roomId : relevantRooms) {
                for (Hold h : holds.activeForRoom(roomId, date)) {
                    if (!h.id().equals(opt.excludeHoldId())) {
                        roomBusy.computeIfAbsent(roomId, k -> new ArrayList<>()).add(new Interval(h.startAt(), h.endAt()));
                    }
                }
            }
        }

        SlotCalculator.Request req = new SlotCalculator.Request(
                doctor.getId(), date, zone, windows, open.get()[0], open.get()[1],
                type.getDurationMinutes(), clinic.getSlotGranularityMinutes(), clinic.getBufferMinutes(),
                doctorBusy, blocked, roomBusy, mode, type.getRequiresRoomType(), requiredRooms,
                now, opt.ignoreNotice() ? Duration.ZERO : Duration.ofMinutes(clinic.getMinNoticeMinutes()),
                clinic.getMaxAdvanceDays());
        return SlotCalculator.compute(req);
    }
}

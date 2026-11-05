package com.coolapp.medibook.doctor;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentType;
import com.coolapp.medibook.appointment.AppointmentTypeRepository;
import com.coolapp.medibook.appointment.BookingService;
import com.coolapp.medibook.appointment.CancelledBy;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.auth.AuthService;
import com.coolapp.medibook.auth.Role;
import com.coolapp.medibook.auth.User;
import com.coolapp.medibook.auth.UserRepository;
import com.coolapp.medibook.availability.AvailabilityService;
import com.coolapp.medibook.availability.Slot;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.clinic.Specialty;
import com.coolapp.medibook.clinic.SpecialtyRepository;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.consultation.ConsultationDtos.EmergencyBlockRequest;
import com.coolapp.medibook.consultation.ConsultationDtos.EmergencyBlockResult;
import com.coolapp.medibook.doctor.DoctorDtos.*;
import com.coolapp.medibook.patient.Gender;
import com.coolapp.medibook.review.ReviewRepository;
import com.coolapp.medibook.review.ReviewStatus;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctors;
    private final DoctorSessionRepository sessions;
    private final DoctorLeaveRepository leaves;
    private final SpecialtyRepository specialties;
    private final AppointmentTypeRepository types;
    private final AppointmentRepository appointments;
    private final ReviewRepository reviews;
    private final AvailabilityService availability;
    private final BookingService booking;
    private final ClinicService clinicService;
    private final UserRepository users;
    private final AuthService authService;
    private final AuditService audit;
    private final Clock clock;

    // ---------- Public directory ----------

    @Transactional(readOnly = true)
    public Page<DoctorCard> search(UUID specialtyId, String q, String language, Gender gender, Pageable pageable) {
        Specification<Doctor> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isTrue(root.<Boolean>get("active")));
            p.add(cb.isFalse(root.<Boolean>get("deleted")));
            if (specialtyId != null) {
                p.add(cb.equal(root.get("specialtyId"), specialtyId));
            }
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.<String>get("fullName")), "%" + q.trim().toLowerCase() + "%"));
            }
            if (language != null && !language.isBlank()) {
                p.add(cb.like(cb.lower(root.<String>get("languages")), "%" + language.trim().toLowerCase() + "%"));
            }
            if (gender != null) {
                p.add(cb.equal(root.get("gender"), gender));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return doctors.findAll(spec, pageable).map(d -> card(d, true));
    }

    @Transactional(readOnly = true)
    public DoctorCard get(UUID id) {
        return card(doctors.findById(id).filter(Doctor::isActive).orElseThrow(() -> ApiException.notFound("Doctor")), true);
    }

    public DoctorCard card(Doctor d, boolean withNextSlot) {
        String specialty = specialties.findById(d.getSpecialtyId()).map(Specialty::getName).orElse(null);
        Instant next = null;
        if (withNextSlot) {
            AppointmentType firstType = types.findBySpecialtyIdAndActiveTrueOrderByNameAsc(d.getSpecialtyId()).stream()
                    .filter(t -> !t.isFollowUp()).findFirst().orElse(null);
            if (firstType != null) {
                next = availability.nextAvailable(d, firstType, 7).map(Slot::startAt).orElse(null);
            }
        }
        return new DoctorCard(d.getId(), d.getFullName(), d.getGender(), d.getSpecialtyId(), specialty, d.getQualifications(),
                d.getExperienceYears(), d.languageList(), d.getBio(), d.getConsultationFee(), d.getPhotoUrl(),
                reviews.averageApproved(d.getId()), reviews.countByDoctorIdAndStatus(d.getId(), ReviewStatus.APPROVED),
                next, d.isActive());
    }

    // ---------- Doctor self-service ----------

    public Doctor me() {
        return doctors.findByUserId(CurrentUser.get().id()).orElseThrow(() -> ApiException.forbidden("Doctor profile required"));
    }

    @Transactional(readOnly = true)
    public List<SessionDto> mySessions() {
        return sessions.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(me().getId()).stream().map(SessionDto::from).toList();
    }

    /** Replaces the weekly schedule. Rejects overlaps with own sessions or another doctor's use of a room. */
    @Transactional
    public List<SessionDto> replaceSessions(UUID doctorId, List<SessionRequest> req) {
        for (int i = 0; i < req.size(); i++) {
            SessionRequest a = req.get(i);
            if (!a.startTime().isBefore(a.endTime())) {
                throw ApiException.badRequest(a.dayOfWeek() + ": start must be before end");
            }
            if (a.mode() == ConsultationMode.VIDEO && a.roomId() != null) {
                throw ApiException.badRequest("Video sessions don't use a room");
            }
            for (int j = i + 1; j < req.size(); j++) {
                SessionRequest b = req.get(j);
                if (a.dayOfWeek() == b.dayOfWeek() && a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime())) {
                    throw ApiException.badRequest("Overlapping sessions on " + a.dayOfWeek());
                }
            }
            if (a.roomId() != null) {
                for (DoctorSession other : sessions.findByRoomIdAndDayOfWeek(a.roomId(), a.dayOfWeek())) {
                    if (!other.getDoctorId().equals(doctorId)
                            && a.startTime().isBefore(other.getEndTime()) && other.getStartTime().isBefore(a.endTime())) {
                        throw ApiException.conflict("Room is already used by another doctor on " + a.dayOfWeek()
                                + " " + other.getStartTime() + "-" + other.getEndTime());
                    }
                }
            }
        }
        sessions.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).forEach(s -> s.setDeleted(true));
        sessions.flush();
        for (SessionRequest r : req) {
            DoctorSession s = new DoctorSession();
            s.setDoctorId(doctorId);
            s.setDayOfWeek(r.dayOfWeek());
            s.setStartTime(r.startTime());
            s.setEndTime(r.endTime());
            s.setRoomId(r.roomId());
            s.setMode(r.mode());
            sessions.save(s);
        }
        audit.log("DOCTOR_SESSIONS_UPDATED", "Doctor", doctorId, req.size() + " sessions");
        return sessions.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream().map(SessionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveDto> myLeave() {
        return leaves.findByDoctorIdAndEndAtAfterOrderByStartAtAsc(me().getId(), clock.instant()).stream().map(LeaveDto::from).toList();
    }

    @Transactional
    public LeaveDto addLeave(LeaveRequest req) {
        Doctor d = me();
        if (!req.startAt().isBefore(req.endAt())) {
            throw ApiException.badRequest("Leave must end after it starts");
        }
        List<Appointment> clash = appointments.findForDoctorOverlapping(d.getId(),
                EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_PAYMENT, AppointmentStatus.CHECKED_IN),
                req.startAt(), req.endAt());
        if (!clash.isEmpty()) {
            throw ApiException.conflict(clash.size() + " booked appointment(s) fall in this period - use the emergency block to cancel them, or ask reception to reschedule");
        }
        DoctorLeave l = new DoctorLeave();
        l.setDoctorId(d.getId());
        l.setStartAt(req.startAt());
        l.setEndAt(req.endAt());
        l.setReason(req.reason());
        l.setKind(req.kind() == null ? LeaveKind.LEAVE : req.kind());
        leaves.save(l);
        audit.log("DOCTOR_LEAVE_ADDED", "DoctorLeave", l.getId(), null);
        return LeaveDto.from(l);
    }

    @Transactional
    public void deleteLeave(UUID id) {
        DoctorLeave l = leaves.findById(id).orElseThrow(() -> ApiException.notFound("Leave"));
        if (!l.getDoctorId().equals(me().getId())) {
            throw ApiException.forbidden("Not your leave");
        }
        l.setDeleted(true);
        audit.log("DOCTOR_LEAVE_DELETED", "DoctorLeave", id, null);
    }

    /** Blocks the rest of the day (default) and cancels affected bookings with a full refund + reschedule email. */
    @Transactional
    public EmergencyBlockResult emergencyBlock(UUID doctorId, EmergencyBlockRequest req) {
        ZoneId zone = clinicService.zone();
        Instant from = req.from() == null ? clock.instant() : req.from();
        Instant to = req.to() == null ? LocalDate.now(clock.withZone(zone)).plusDays(1).atStartOfDay(zone).toInstant() : req.to();
        if (!from.isBefore(to)) {
            throw ApiException.badRequest("Block must end after it starts");
        }
        DoctorLeave l = new DoctorLeave();
        l.setDoctorId(doctorId);
        l.setStartAt(from);
        l.setEndAt(to);
        l.setReason(req.reason());
        l.setKind(LeaveKind.EMERGENCY);
        leaves.save(l);

        List<Appointment> affected = appointments.findForDoctorOverlapping(doctorId,
                EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_PAYMENT), from, to);
        for (Appointment a : affected) {
            booking.cancelInternal(a, CancelledBy.DOCTOR_EMERGENCY, "Doctor unavailable: " + req.reason());
        }
        audit.log("DOCTOR_EMERGENCY_BLOCK", "Doctor", doctorId, affected.size() + " appointments cancelled");
        return new EmergencyBlockResult(affected.size());
    }

    // ---------- Admin ----------

    @Transactional
    public DoctorCard invite(DoctorInviteRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Email already in use");
        }
        specialties.findById(req.profile().specialtyId()).orElseThrow(() -> ApiException.notFound("Specialty"));
        User u = new User();
        u.setEmail(req.email().trim().toLowerCase());
        u.setPhone(req.phone());
        u.setRole(Role.DOCTOR);
        users.save(u);
        Doctor d = new Doctor();
        d.setUserId(u.getId());
        apply(d, req.profile());
        doctors.save(d);
        authService.sendInvite(u);
        audit.log("DOCTOR_INVITED", "Doctor", d.getId(), req.email());
        return card(d, false);
    }

    @Transactional
    public DoctorCard update(UUID id, DoctorProfileRequest req) {
        Doctor d = doctors.findById(id).orElseThrow(() -> ApiException.notFound("Doctor"));
        apply(d, req);
        if (req.active() != null) {
            d.setActive(req.active());
            users.findById(d.getUserId()).ifPresent(u -> u.setActive(req.active()));
        }
        audit.log("DOCTOR_UPDATED", "Doctor", id, null);
        return card(d, false);
    }

    @Transactional(readOnly = true)
    public List<DoctorCard> all() {
        return doctors.findAll().stream().filter(d -> !d.isDeleted()).map(d -> card(d, false)).toList();
    }

    private static void apply(Doctor d, DoctorProfileRequest p) {
        d.setFullName(p.fullName());
        d.setGender(p.gender());
        d.setSpecialtyId(Objects.requireNonNull(p.specialtyId()));
        d.setQualifications(p.qualifications());
        d.setExperienceYears(p.experienceYears());
        d.setLanguages(p.languages());
        d.setBio(p.bio());
        d.setLicenseNo(p.licenseNo());
        d.setConsultationFee(p.consultationFee());
        d.setPhotoUrl(p.photoUrl());
    }
}

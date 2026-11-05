package com.coolapp.medibook.appointment;

import com.coolapp.medibook.appointment.AppointmentDtos.*;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.auth.Role;
import com.coolapp.medibook.availability.AvailabilityService;
import com.coolapp.medibook.availability.FollowUpPolicy;
import com.coolapp.medibook.availability.Slot;
import com.coolapp.medibook.clinic.Clinic;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.AuthUser;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.notification.NotificationService;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientAccessService;
import com.coolapp.medibook.payment.PaymentService;
import com.coolapp.medibook.payment.RefundPolicy;
import com.coolapp.medibook.video.VideoLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Booking flow: hold -> book (re-validating every rule inside one transaction) -> pay.
 * The Redis hold gives a fast, friendly lock; the PostgreSQL exclusion constraints are the
 * guarantee against double-booking a doctor, a room or a patient.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final HoldService holds;
    private final AvailabilityService availability;
    private final AppointmentRepository appointments;
    private final AppointmentTypeRepository types;
    private final IntakeFormRepository intakeForms;
    private final DoctorRepository doctors;
    private final PatientAccessService access;
    private final ClinicService clinicService;
    private final PaymentService paymentService;
    private final NotificationService notifications;
    private final VideoLinkService video;
    private final AppointmentViews views;
    private final AuditService audit;
    private final Clock clock;

    // ---------- Holds ----------

    public HoldResponse createHold(HoldRequest req) {
        AuthUser user = CurrentUser.get();
        if (req.patientId() != null) {
            access.requireBookingAccess(req.patientId());
        }
        return HoldResponse.from(acquire(user.id(), req.doctorId(), req.typeId(), req.startAt(), req.mode(), req.patientId(), false));
    }

    public void releaseHold(UUID holdId) {
        Hold hold = holds.find(holdId).orElseThrow(() -> ApiException.notFound("Hold"));
        if (!hold.userId().equals(CurrentUser.get().id())) {
            throw ApiException.forbidden("Not your hold");
        }
        holds.release(hold);
    }

    private Hold acquire(UUID userId, UUID doctorId, UUID typeId, Instant startAt, ConsultationMode mode,
                         UUID patientId, boolean walkIn) {
        LocalDate day = startAt.atZone(clinicService.zone()).toLocalDate();
        List<Slot> slots = availability.forDoctor(doctorId, typeId, day,
                new AvailabilityService.Options(mode, patientId, null, walkIn));
        Slot slot = slots.stream()
                .filter(s -> s.startAt().equals(startAt) && s.mode() == mode)
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("That time is no longer available"));
        return holds.tryAcquire(userId, doctorId, slot.roomId(), typeId, slot.startAt(), slot.endAt(), mode, day)
                .orElseThrow(() -> ApiException.conflict("Someone else is booking that time - please pick another"));
    }

    // ---------- Booking ----------

    @Transactional
    public BookingResponse book(BookRequest req) {
        AuthUser user = CurrentUser.get();
        Hold hold = holds.find(req.holdId())
                .orElseThrow(() -> ApiException.conflict("Your hold expired - please choose the time again"));
        if (!hold.userId().equals(user.id())) {
            throw ApiException.forbidden("Not your hold");
        }
        Patient patient = access.requireBookingAccess(req.patientId());
        AppointmentSource source = user.is(Role.PATIENT) ? AppointmentSource.ONLINE : AppointmentSource.PHONE;
        return createFromHold(hold, patient, req.intakeForm(), req.paymentMode(), source);
    }

    /** Front desk phone booking: hold + book in one call. */
    @Transactional
    public BookingResponse bookForPatient(ReceptionBookRequest req) {
        AuthUser user = CurrentUser.get();
        Patient patient = access.requireBookingAccess(req.patientId());
        Hold hold = acquire(user.id(), req.doctorId(), req.typeId(), req.startAt(), req.mode(), patient.getId(), false);
        return createFromHold(hold, patient, req.intakeForm(), null, AppointmentSource.PHONE);
    }

    /** Walk-in: first free in-person slot today, ignoring the minimum-notice rule. */
    @Transactional
    public Appointment bookWalkIn(UUID doctorId, WalkInRequest req) {
        AuthUser user = CurrentUser.get();
        Patient patient = access.requireBookingAccess(req.patientId());
        LocalDate today = LocalDate.now(clock.withZone(clinicService.zone()));
        Slot first = availability.forDoctor(doctorId, req.typeId(), today,
                        new AvailabilityService.Options(ConsultationMode.IN_PERSON, patient.getId(), null, true))
                .stream().findFirst()
                .orElseThrow(() -> ApiException.conflict("No free slot left today for this doctor"));
        Hold hold = holds.tryAcquire(user.id(), doctorId, first.roomId(), req.typeId(), first.startAt(), first.endAt(),
                        ConsultationMode.IN_PERSON, today)
                .orElseThrow(() -> ApiException.conflict("Slot was just taken - try again"));
        BookingResponse r = createFromHold(hold, patient, null, null, AppointmentSource.WALK_IN);
        return appointments.findById(r.appointmentId()).orElseThrow();
    }

    private BookingResponse createFromHold(Hold hold, Patient patient, IntakeFormRequest intake,
                                           PaymentMode requestedPayment, AppointmentSource source) {
        AuthUser user = CurrentUser.get();
        Clinic clinic = clinicService.get();
        Instant now = clock.instant();
        AppointmentType type = types.findById(hold.typeId()).orElseThrow(() -> ApiException.notFound("Appointment type"));
        Doctor doctor = doctors.findById(hold.doctorId()).filter(Doctor::isActive)
                .orElseThrow(() -> ApiException.notFound("Doctor"));

        // Re-validate rules (they may have changed since the hold was placed).
        if (source != AppointmentSource.WALK_IN) {
            if (hold.startAt().isBefore(now.plus(Duration.ofMinutes(clinic.getMinNoticeMinutes())))) {
                throw ApiException.conflict("Appointments need at least " + clinic.getMinNoticeMinutes() + " minutes' notice");
            }
            if (hold.startAt().isAfter(now.plus(Duration.ofDays(clinic.getMaxAdvanceDays() + 1L)))) {
                throw ApiException.badRequest("Too far in advance");
            }
        }
        if (type.isFollowUp()) {
            int window = type.getFollowUpWindowDays() == null ? 14 : type.getFollowUpWindowDays();
            Instant last = appointments.lastCompletedVisit(doctor.getId(), patient.getId());
            if (!FollowUpPolicy.isEligible(last, now, window)) {
                throw ApiException.badRequest("Follow-ups are only available within " + window + " days of a visit with this doctor");
            }
        }
        if (type.isIntakeRequired() && source == AppointmentSource.ONLINE && intake == null) {
            throw ApiException.badRequest("Please complete the intake form");
        }
        if (intake != null && source == AppointmentSource.ONLINE && !intake.allergiesConfirmed()) {
            throw ApiException.badRequest("Please confirm your allergies");
        }

        BigDecimal fee = type.getFee() != null ? type.getFee() : doctor.getConsultationFee();
        long noShows = appointments.countByPatientIdAndStatus(patient.getId(), AppointmentStatus.NO_SHOW);
        PaymentMode paymentMode = BookingRules.decidePaymentMode(fee, requestedPayment, clinic.getPaymentPolicy(),
                noShows, clinic.getNoShowPrepayThreshold(), !user.is(Role.PATIENT));

        Appointment a = new Appointment();
        a.setPatientId(patient.getId());
        a.setBookedByUserId(user.id());
        a.setDoctorId(doctor.getId());
        a.setRoomId(hold.roomId());
        a.setAppointmentTypeId(type.getId());
        a.setStartAt(hold.startAt());
        a.setEndAt(hold.endAt());
        a.setMode(hold.mode());
        a.setSource(source);
        a.setPaymentMode(paymentMode);
        a.setStatus(paymentMode == PaymentMode.ONLINE ? AppointmentStatus.PENDING_PAYMENT : AppointmentStatus.CONFIRMED);
        if (hold.mode() == ConsultationMode.VIDEO) {
            a.setVideoLink(video.newMeetingLink());
        }
        saveChecked(a);

        if (intake != null) {
            IntakeForm f = new IntakeForm();
            f.setAppointmentId(a.getId());
            f.setReason(intake.reason());
            f.setSymptoms(intake.symptoms());
            f.setDurationOfSymptoms(intake.durationOfSymptoms());
            f.setCurrentMeds(intake.currentMeds());
            f.setAllergiesConfirmed(intake.allergiesConfirmed());
            f.setExtraNotes(intake.extraNotes());
            intakeForms.save(f);
        }

        PaymentService.Initiation pay = paymentService.initiate(a, fee);
        if (a.getStatus() == AppointmentStatus.CONFIRMED) {
            notifications.onConfirmed(a);
        }
        audit.log("APPOINTMENT_BOOKED", "Appointment", a.getId(),
                "status=" + a.getStatus() + " source=" + source + " payment=" + paymentMode);
        releaseAfterCommit(hold);

        String map = clinic.getAddress() == null ? null
                : "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(clinic.getAddress(), StandardCharsets.UTF_8);
        return new BookingResponse(a.getId(), a.getStatus(), a.getStartAt(), a.getEndAt(), doctor.getFullName(), a.getMode(),
                paymentMode, fee, pay.clientSecret(), clinic.getName(), clinic.getAddress(), map,
                a.getMode() == ConsultationMode.VIDEO);
    }

    // ---------- Reschedule / cancel ----------

    @Transactional
    public AppointmentDto reschedule(UUID appointmentId, UUID holdId) {
        AuthUser user = CurrentUser.get();
        Appointment a = load(appointmentId);
        requireManage(user, a);
        if (a.getStatus() != AppointmentStatus.CONFIRMED && a.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("Only upcoming appointments can be rescheduled");
        }
        Clinic clinic = clinicService.get();
        if (user.is(Role.PATIENT) && !RefundPolicy.isBeforeCutoff(a.getStartAt(), clock.instant(), clinic.getCancellationCutoffHours())) {
            throw ApiException.conflict("Rescheduling closes " + clinic.getCancellationCutoffHours() + " hours before the visit - please call the clinic");
        }
        Hold hold = holds.find(holdId).orElseThrow(() -> ApiException.conflict("Your hold expired - choose the time again"));
        if (!hold.userId().equals(user.id())) {
            throw ApiException.forbidden("Not your hold");
        }
        if (!hold.typeId().equals(a.getAppointmentTypeId())) {
            throw ApiException.badRequest("The new slot must be for the same appointment type");
        }
        Instant oldStart = a.getStartAt();
        a.setDoctorId(hold.doctorId());
        a.setStartAt(hold.startAt());
        a.setEndAt(hold.endAt());
        a.setRoomId(hold.roomId());
        if (hold.mode() != a.getMode()) {
            a.setMode(hold.mode());
            a.setVideoLink(hold.mode() == ConsultationMode.VIDEO ? video.newMeetingLink() : null);
        }
        saveChecked(a);
        notifications.onRescheduled(a);
        audit.log("APPOINTMENT_RESCHEDULED", "Appointment", a.getId(), oldStart + " -> " + a.getStartAt());
        releaseAfterCommit(hold);
        return views.one(a);
    }

    @Transactional
    public AppointmentDto cancel(UUID appointmentId, String reason) {
        AuthUser user = CurrentUser.get();
        Appointment a = load(appointmentId);
        requireManage(user, a);
        if (user.is(Role.PATIENT) && !a.getStartAt().isAfter(clock.instant())) {
            throw ApiException.conflict("This appointment has already started");
        }
        CancelledBy by = user.is(Role.PATIENT) ? CancelledBy.PATIENT : CancelledBy.CLINIC;
        cancelInternal(a, by, reason);
        return views.one(a);
    }

    /** Shared by patient/clinic cancellation and the doctor's emergency block. */
    @Transactional
    public void cancelInternal(Appointment a, CancelledBy by, String reason) {
        AppointmentStatus from = a.transitionTo(AppointmentStatus.CANCELLED);
        a.setCancelledBy(by);
        a.setCancelReason(reason);
        BigDecimal refunded = paymentService.refundForCancellation(a, by);
        notifications.onCancelled(a);
        audit.log("APPOINTMENT_CANCELLED", "Appointment", a.getId(),
                from + " -> CANCELLED by " + by + (refunded.signum() > 0 ? ", refunded " + refunded : ""));
    }

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public List<AppointmentDto> myAppointments(String scope) {
        List<UUID> ids = access.myPatientIds();
        Instant now = clock.instant();
        List<Appointment> list = "past".equalsIgnoreCase(scope)
                ? appointments.findPast(ids, AppointmentStatus.ACTIVE, now)
                : appointments.findUpcoming(ids, AppointmentStatus.ACTIVE, now);
        return views.many(list);
    }

    @Transactional(readOnly = true)
    public AppointmentDto get(UUID id) {
        AuthUser user = CurrentUser.get();
        Appointment a = load(id);
        requireView(user, a);
        return views.one(a);
    }

    @Transactional(readOnly = true)
    public VideoLinkDto videoLink(UUID id) {
        AuthUser user = CurrentUser.get();
        Appointment a = load(id);
        boolean participant = (user.is(Role.PATIENT) && access.isSelfOrGuardian(user, access.load(a.getPatientId())))
                || (user.is(Role.DOCTOR) && doctors.findByUserId(user.id()).map(d -> d.getId().equals(a.getDoctorId())).orElse(false));
        if (!participant) {
            throw ApiException.forbidden("Only the patient and doctor can join");
        }
        if (a.getMode() != ConsultationMode.VIDEO || a.getVideoLink() == null) {
            throw ApiException.badRequest("Not a video consultation");
        }
        if (!a.getStatus().isActive()) {
            throw ApiException.conflict("This consultation is " + a.getStatus());
        }
        Instant from = a.getStartAt().minus(Duration.ofMinutes(10));
        Instant until = a.getEndAt().plus(Duration.ofMinutes(30));
        Instant now = clock.instant();
        if (now.isBefore(from) || now.isAfter(until)) {
            throw ApiException.forbidden("The Join button opens 10 minutes before the start time");
        }
        return new VideoLinkDto(a.getVideoLink(), from, until);
    }

    /** Loads an appointment the current user is allowed to see (patient/guardian, its doctor, or staff). */
    @Transactional(readOnly = true)
    public Appointment loadForView(UUID id) {
        Appointment a = load(id);
        requireView(CurrentUser.get(), a);
        return a;
    }

    public Appointment load(UUID id) {
        return appointments.findById(id).orElseThrow(() -> ApiException.notFound("Appointment"));
    }

    private void requireManage(AuthUser user, Appointment a) {
        switch (user.role()) {
            case PATIENT -> {
                if (!access.isSelfOrGuardian(user, access.load(a.getPatientId()))) {
                    throw ApiException.forbidden("Not your appointment");
                }
            }
            case RECEPTIONIST, ADMIN, SUPER_ADMIN -> { }
            case DOCTOR -> throw ApiException.forbidden("Doctors cancel via the emergency block");
        }
    }

    private void requireView(AuthUser user, Appointment a) {
        boolean ok = switch (user.role()) {
            case PATIENT -> access.isSelfOrGuardian(user, access.load(a.getPatientId()));
            case DOCTOR -> doctors.findByUserId(user.id()).map(d -> Objects.equals(d.getId(), a.getDoctorId())).orElse(false);
            case RECEPTIONIST, ADMIN, SUPER_ADMIN -> true;
        };
        if (!ok) {
            throw ApiException.forbidden("Not your appointment");
        }
    }

    private void saveChecked(Appointment a) {
        try {
            appointments.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            String msg = String.valueOf(e.getMostSpecificCause().getMessage());
            if (msg.contains("no_patient_overlap")) {
                throw ApiException.conflict("This patient already has an appointment at that time");
            }
            if (msg.contains("no_room_overlap")) {
                throw ApiException.conflict("The required room was just booked - please pick another time");
            }
            throw ApiException.conflict("That time was just booked - please pick another");
        }
    }

    private void releaseAfterCommit(Hold hold) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    holds.release(hold);
                }
            });
        } else {
            holds.release(hold);
        }
    }

    // ---------- Jobs ----------

    /** PENDING_PAYMENT bookings whose payment never arrived become EXPIRED, freeing the slot. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void expireUnpaid() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(15));
        for (Appointment a : appointments.findByStatusAndCreatedAtBefore(AppointmentStatus.PENDING_PAYMENT, cutoff)) {
            a.transitionTo(AppointmentStatus.EXPIRED);
            audit.log("APPOINTMENT_EXPIRED", "Appointment", a.getId(), "payment not received");
        }
    }
}

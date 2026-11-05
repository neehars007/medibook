package com.coolapp.medibook.notification;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentTypeRepository;
import com.coolapp.medibook.auth.User;
import com.coolapp.medibook.auth.UserRepository;
import com.coolapp.medibook.clinic.Clinic;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.common.config.AppProperties;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientRepository;
import com.coolapp.medibook.payment.Payment;
import com.coolapp.medibook.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Queues notifications as rows and dispatches them on a schedule. Emails only ever link to the
 * secure portal: no diagnoses, no medication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final EnumSet<NotificationType> REMINDERS = EnumSet.of(NotificationType.REMINDER_24H, NotificationType.REMINDER_2H);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a z");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationRepository notifications;
    private final AppointmentRepository appointments;
    private final AppointmentTypeRepository types;
    private final PatientRepository patients;
    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PaymentRepository payments;
    private final ClinicService clinicService;
    private final EmailSender email;
    private final AppProperties props;
    private final Clock clock;

    // ---------- Queueing (called inside business transactions) ----------

    public void onConfirmed(Appointment a) {
        queue(a, NotificationType.BOOKING_CONFIRMED, clock.instant());
        queueReminders(a);
    }

    public void onRescheduled(Appointment a) {
        cancelPending(a.getId(), REMINDERS);
        queue(a, NotificationType.RESCHEDULED, clock.instant());
        queueReminders(a);
    }

    public void onCancelled(Appointment a) {
        cancelPending(a.getId(), REMINDERS);
        queue(a, a.getCancelledBy() == com.coolapp.medibook.appointment.CancelledBy.DOCTOR_EMERGENCY
                ? NotificationType.EMERGENCY_BLOCK : NotificationType.CANCELLED, clock.instant());
    }

    public void onVisitCompleted(Appointment a, Integer followUpInDays) {
        queue(a, NotificationType.VISIT_COMPLETED, clock.instant());
        if (followUpInDays != null && followUpInDays > 0) {
            Instant at = clock.instant().plus(Duration.ofDays(Math.max(0, followUpInDays - 2)));
            queue(a, NotificationType.FOLLOW_UP_DUE, at);
        }
    }

    private void queueReminders(Appointment a) {
        Instant now = clock.instant();
        Instant r24 = a.getStartAt().minus(Duration.ofHours(24));
        Instant r2 = a.getStartAt().minus(Duration.ofHours(2));
        if (r24.isAfter(now)) {
            queue(a, NotificationType.REMINDER_24H, r24);
        }
        if (r2.isAfter(now)) {
            queue(a, NotificationType.REMINDER_2H, r2);
        }
    }

    private void queue(Appointment a, NotificationType type, Instant at) {
        Optional<String> to = recipientEmail(a.getPatientId());
        if (to.isEmpty()) {
            log.debug("No email for patient {}, skipping {}", a.getPatientId(), type);
            return;
        }
        Notification n = new Notification();
        n.setAppointmentId(a.getId());
        n.setRecipientEmail(to.get());
        n.setType(type);
        n.setScheduledAt(at);
        notifications.save(n);
    }

    private void cancelPending(UUID appointmentId, EnumSet<NotificationType> types) {
        notifications.findByAppointmentIdAndStatusAndTypeIn(appointmentId, NotificationStatus.PENDING, types)
                .forEach(n -> n.setStatus(NotificationStatus.CANCELLED));
    }

    /** Dependents have no login: notifications go to the guardian's email. */
    private Optional<String> recipientEmail(UUID patientId) {
        Patient p = patients.findById(patientId).orElse(null);
        if (p == null) {
            return Optional.empty();
        }
        UUID userId = p.getUserId();
        if (userId == null && p.getGuardianPatientId() != null) {
            userId = patients.findById(p.getGuardianPatientId()).map(Patient::getUserId).orElse(null);
        }
        return userId == null ? Optional.empty() : users.findById(userId).map(User::getEmail);
    }

    // ---------- Dispatch ----------

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    @Transactional
    public void dispatchDue() {
        List<Notification> due = notifications.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                NotificationStatus.PENDING, clock.instant(), PageRequest.of(0, 50));
        for (Notification n : due) {
            try {
                Appointment a = appointments.findById(n.getAppointmentId()).orElse(null);
                if (a == null || (REMINDERS.contains(n.getType()) && a.getStatus() != AppointmentStatus.CONFIRMED
                        && a.getStatus() != AppointmentStatus.PENDING_PAYMENT)) {
                    n.setStatus(NotificationStatus.CANCELLED);
                    continue;
                }
                Rendered r = render(n.getType(), a);
                boolean ok = email.send(n.getRecipientEmail(), r.subject(), r.content(), r.attachment());
                n.setStatus(ok ? NotificationStatus.SENT : NotificationStatus.FAILED);
                n.setSentAt(ok ? clock.instant() : null);
                n.setLastError(ok ? null : "SMTP send failed");
            } catch (RuntimeException e) {
                n.setStatus(NotificationStatus.FAILED);
                n.setLastError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().substring(0, Math.min(500, e.getMessage().length())));
            }
        }
    }

    /** Morning digest of the day's schedule for each doctor (07:00 clinic time). */
    @Scheduled(cron = "0 0 7 * * *", zone = "${medibook.app.timezone:America/New_York}")
    @Transactional(readOnly = true)
    public void doctorDailyDigest() {
        ZoneId zone = clinicService.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        for (Doctor d : doctors.findByActiveTrue()) {
            List<Appointment> list = appointments.findByDoctorIdAndStartAtBetweenOrderByStartAtAsc(d.getId(), from, to)
                    .stream().filter(a -> a.getStatus().isActive()).toList();
            if (list.isEmpty()) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            lines.add("You have " + list.size() + " appointment(s) today:");
            for (Appointment a : list) {
                String typeName = types.findById(a.getAppointmentTypeId()).map(t -> t.getName()).orElse("Visit");
                String initials = patients.findById(a.getPatientId()).map(p -> initials(p.getFullName())).orElse("?");
                lines.add(TIME.format(a.getStartAt().atZone(zone)) + "  " + typeName + "  (" + initials + ", "
                        + (a.getMode() == ConsultationMode.VIDEO ? "video" : "in person") + ")");
            }
            users.findById(d.getUserId()).ifPresent(u -> email.send(u.getEmail(), "Your schedule for " + today,
                    new EmailContent("Today's schedule", "Good morning, " + d.getFullName() + ".", lines,
                            "Open today's view", props.app().frontendUrl() + "/doctor/today"), null));
        }
    }

    // ---------- Rendering ----------

    private record Rendered(String subject, EmailContent content, EmailAttachment attachment) {}

    private Rendered render(NotificationType type, Appointment a) {
        Clinic clinic = clinicService.get();
        ZoneId zone = clinic.zone();
        String doctorName = doctors.findById(a.getDoctorId()).map(Doctor::getFullName).orElse("your doctor");
        String patientName = patients.findById(a.getPatientId()).map(Patient::getFullName).orElse("");
        String when = WHEN.format(a.getStartAt().atZone(zone));
        String portal = props.app().frontendUrl();
        boolean video = a.getMode() == ConsultationMode.VIDEO;
        String where = video ? "Video consultation - join from your patient portal (the Join button opens 10 minutes before)."
                : clinic.getName() + ", " + clinic.getAddress();
        String map = clinic.getAddress() == null ? null
                : "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(clinic.getAddress(), StandardCharsets.UTF_8);
        String greeting = "Hello,";
        String forWhom = "Appointment for " + patientName + " with " + doctorName + ".";

        return switch (type) {
            case BOOKING_CONFIRMED -> {
                List<String> lines = new ArrayList<>(List.of(forWhom, "When: " + when, "Where: " + where));
                if (!video && map != null) {
                    lines.add("Map: " + map);
                }
                lines.add("Appointment ID: " + a.getId());
                lines.add("Please arrive 10 minutes early with a photo ID and your insurance card.");
                byte[] ics = IcsBuilder.build(a.getId(), a.getStartAt(), a.getEndAt(), "Appointment with " + doctorName,
                        video ? "Video consultation" : clinic.getAddress(), "Manage at " + portal + "/me/appointments", clock.instant());
                yield new Rendered("Appointment confirmed - " + when,
                        new EmailContent("Your appointment is confirmed", greeting, lines, "View appointment", portal + "/me/appointments"),
                        new EmailAttachment("appointment.ics", ics, "text/calendar"));
            }
            case REMINDER_24H, REMINDER_2H -> new Rendered(
                    (type == NotificationType.REMINDER_24H ? "Reminder: appointment tomorrow" : "Reminder: appointment in 2 hours"),
                    new EmailContent("Appointment reminder", greeting,
                            List.of(forWhom, "When: " + when, "Where: " + where,
                                    "Please bring any previous reports or test results."),
                            "View appointment", portal + "/me/appointments"), null);
            case RESCHEDULED -> new Rendered("Appointment rescheduled",
                    new EmailContent("Your appointment was rescheduled", greeting,
                            List.of(forWhom, "New time: " + when, "Where: " + where),
                            "View appointment", portal + "/me/appointments"), null);
            case CANCELLED -> new Rendered("Appointment cancelled",
                    new EmailContent("Your appointment was cancelled", greeting,
                            List.of(forWhom, "Was scheduled for: " + when,
                                    "Reason: " + (a.getCancelReason() == null ? "not specified" : a.getCancelReason()),
                                    refundLine(a.getId())),
                            "Book again", portal + "/book"), null);
            case EMERGENCY_BLOCK -> new Rendered("We're sorry - your appointment needs to be moved",
                    new EmailContent("Your appointment was cancelled", greeting,
                            List.of("We're very sorry. " + doctorName + " is unavailable due to an emergency and your appointment on "
                                            + when + " was cancelled.",
                                    refundLine(a.getId()), "Please pick a new time that suits you."),
                            "Reschedule now", portal + "/book?doctorId=" + a.getDoctorId()), null);
            case VISIT_COMPLETED -> new Rendered("Your visit summary is ready",
                    new EmailContent("Your visit summary is ready", greeting,
                            List.of("The summary and any prescription from your visit with " + doctorName
                                    + " are available in your secure patient portal."),
                            "Open visit summary", portal + "/me/visits/" + a.getId()), null);
            case FOLLOW_UP_DUE -> new Rendered("Time to book your follow-up",
                    new EmailContent("Follow-up recommended", greeting,
                            List.of(doctorName + " recommended a follow-up visit for " + patientName + ". Book now to get a convenient time."),
                            "Book follow-up", portal + "/book?doctorId=" + a.getDoctorId()), null);
        };
    }

    private String refundLine(UUID appointmentId) {
        return payments.findFirstByAppointmentIdOrderByCreatedAtDesc(appointmentId)
                .filter(p -> p.getRefundedAmount() != null && p.getRefundedAmount().signum() > 0)
                .map(p -> "A refund of $" + p.getRefundedAmount().setScale(2, java.math.RoundingMode.HALF_UP)
                        + " has been issued to your original payment method.")
                .orElseGet(() -> payments.findFirstByAppointmentIdOrderByCreatedAtDesc(appointmentId)
                        .filter(p -> p.getStatus().wasCollected())
                        .map(Payment::getAmount).filter(x -> x.compareTo(BigDecimal.ZERO) > 0)
                        .map(x -> "No refund applies under the clinic's cancellation policy.")
                        .orElse("No payment was taken for this appointment."));
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append('.');
            }
        }
        return sb.toString();
    }
}

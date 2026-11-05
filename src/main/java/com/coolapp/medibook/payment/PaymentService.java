package com.coolapp.medibook.payment;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.CancelledBy;
import com.coolapp.medibook.appointment.PaymentMode;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.clinic.Clinic;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.notification.NotificationService;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientRepository;
import com.coolapp.medibook.prescription.PdfService;
import com.coolapp.medibook.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private final PaymentRepository payments;
    private final AppointmentRepository appointments;
    private final StripeGateway stripe;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PdfService pdf;
    private final StorageService storage;
    private final ClinicService clinicService;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final Clock clock;

    public record Initiation(String clientSecret, boolean confirmedImmediately) {}

    /**
     * Called by booking. ONLINE creates a Stripe PaymentIntent (appointment stays PENDING_PAYMENT
     * until the webhook); in simulated mode it succeeds at once. PAY_AT_CLINIC records a pending
     * payment the receptionist settles at check-in. FREE creates no payment row.
     */
    @Transactional
    public Initiation initiate(Appointment a, BigDecimal amount) {
        if (a.getPaymentMode() == PaymentMode.FREE) {
            return new Initiation(null, true);
        }
        Payment p = new Payment();
        p.setAppointmentId(a.getId());
        p.setAmount(amount);
        p.setStatus(PaymentStatus.PENDING);
        if (a.getPaymentMode() == PaymentMode.PAY_AT_CLINIC) {
            payments.save(p);
            return new Initiation(null, true);
        }
        StripeGateway.Intent intent = stripe.createIntent(amount, a.getId());
        p.setMode(PaymentMethod.ONLINE);
        p.setStripePaymentIntentId(intent.id());
        payments.save(p);
        if (!stripe.enabled()) {
            markSucceeded(p);
            a.transitionTo(AppointmentStatus.CONFIRMED);
            return new Initiation(null, true);
        }
        return new Initiation(intent.clientSecret(), false);
    }

    @Transactional
    public void handleWebhook(String payload, String signature) {
        StripeGateway.WebhookEvent event;
        try {
            event = stripe.parseWebhook(payload, signature).orElse(null);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        if (event == null) {
            return;
        }
        Payment p = payments.findByStripePaymentIntentId(event.paymentIntentId()).orElse(null);
        if (p == null) {
            log.warn("Webhook for unknown PaymentIntent {}", event.paymentIntentId());
            return;
        }
        Appointment a = appointments.findById(p.getAppointmentId()).orElseThrow();
        switch (event.type()) {
            case "payment_intent.succeeded" -> {
                if (p.getStatus() == PaymentStatus.SUCCEEDED) {
                    return; // idempotent
                }
                markSucceeded(p);
                if (a.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
                    a.transitionTo(AppointmentStatus.CONFIRMED);
                    audit.log("APPOINTMENT_CONFIRMED", "Appointment", a.getId(), "payment received");
                    notifications.onConfirmed(a);
                } else {
                    // Paid after the hold expired: give the money back.
                    refund(p, p.getAmount(), "late payment for " + a.getStatus());
                }
            }
            case "payment_intent.payment_failed" -> {
                p.setStatus(PaymentStatus.FAILED);
                audit.log("PAYMENT_FAILED", "Payment", p.getId(), null);
            }
            default -> log.debug("Ignoring Stripe event {}", event.type());
        }
    }

    /** Receptionist records cash or card collected at the counter. */
    @Transactional
    public Payment recordCounterPayment(Appointment a, PaymentMethod method, BigDecimal amount) {
        if (method == PaymentMethod.ONLINE) {
            throw ApiException.badRequest("Use CASH or CARD_AT_COUNTER");
        }
        Payment p = payments.findFirstByAppointmentIdOrderByCreatedAtDesc(a.getId()).orElseGet(() -> {
            Payment n = new Payment();
            n.setAppointmentId(a.getId());
            n.setAmount(amount);
            n.setStatus(PaymentStatus.PENDING);
            return n;
        });
        if (p.getStatus().wasCollected()) {
            throw ApiException.conflict("Payment already recorded");
        }
        p.setAmount(amount);
        p.setMode(method);
        payments.save(p);
        markSucceeded(p);
        return p;
    }

    /** Applies the refund policy when an appointment is cancelled. Returns the refunded amount. */
    @Transactional
    public BigDecimal refundForCancellation(Appointment a, CancelledBy by) {
        Payment p = payments.findFirstByAppointmentIdOrderByCreatedAtDesc(a.getId()).orElse(null);
        if (p == null) {
            return BigDecimal.ZERO;
        }
        if (p.getStatus() == PaymentStatus.PENDING) {
            p.setStatus(PaymentStatus.FAILED); // nothing collected; close it out
            return BigDecimal.ZERO;
        }
        if (!p.getStatus().wasCollected()) {
            return BigDecimal.ZERO;
        }
        Clinic clinic = clinicService.get();
        BigDecimal amount = RefundPolicy.refundAmount(p.netAmount(), by, a.getStartAt(), clock.instant(),
                clinic.getCancellationCutoffHours());
        if (amount.signum() > 0) {
            refund(p, amount, "cancelled by " + by);
        }
        return amount;
    }

    @Transactional
    public Payment adminRefund(java.util.UUID paymentId, BigDecimal amount) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (!p.getStatus().wasCollected()) {
            throw ApiException.conflict("Nothing collected to refund");
        }
        BigDecimal value = amount == null ? p.netAmount() : amount;
        if (value.signum() <= 0 || value.compareTo(p.netAmount()) > 0) {
            throw ApiException.badRequest("Refund must be between 0 and " + p.netAmount());
        }
        refund(p, value, "admin refund");
        return p;
    }

    @Transactional(readOnly = true)
    public byte[] receiptPdf(Appointment a) {
        Payment p = payments.findFirstByAppointmentIdOrderByCreatedAtDesc(a.getId())
                .filter(x -> x.getStatus().wasCollected())
                .orElseThrow(() -> ApiException.notFound("Receipt"));
        if (p.getReceiptPdfKey() != null) {
            return storage.get(p.getReceiptPdfKey());
        }
        return buildReceipt(p, a);
    }

    private void refund(Payment p, BigDecimal amount, String reason) {
        if (p.getMode() == PaymentMethod.ONLINE) {
            stripe.refund(p.getStripePaymentIntentId(), amount);
        } // counter payments: refunded in cash/card at the desk; we record it.
        p.setRefundedAmount(p.getRefundedAmount().add(amount));
        p.setStatus(p.netAmount().signum() == 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        audit.log("PAYMENT_REFUNDED", "Payment", p.getId(), amount + " (" + reason + ")");
    }

    private void markSucceeded(Payment p) {
        p.setStatus(PaymentStatus.SUCCEEDED);
        p.setPaidAt(clock.instant());
        audit.log("PAYMENT_SUCCEEDED", "Payment", p.getId(), p.getMode() + " " + p.getAmount());
        try {
            Appointment a = appointments.findById(p.getAppointmentId()).orElseThrow();
            byte[] bytes = buildReceipt(p, a);
            String key = "receipts/" + p.getId() + ".pdf";
            storage.put(key, bytes, "application/pdf");
            p.setReceiptPdfKey(key);
        } catch (RuntimeException e) {
            log.warn("Receipt generation deferred for payment {}: {}", p.getId(), e.getMessage());
        }
    }

    private byte[] buildReceipt(Payment p, Appointment a) {
        Clinic c = clinicService.get();
        String patient = patients.findById(a.getPatientId()).map(Patient::getFullName).orElse("");
        String doctor = doctors.findById(a.getDoctorId()).map(Doctor::getFullName).orElse("");
        return pdf.receipt(new PdfService.Receipt(
                new PdfService.Header(c.getName(), c.getAddress(), c.getPhone()),
                p.getId().toString().substring(0, 8).toUpperCase(), patient, doctor,
                FMT.format(a.getStartAt().atZone(c.zone())),
                p.getMode() == null ? "-" : p.getMode().name(), p.getAmount(), p.getRefundedAmount(),
                p.getPaidAt() == null ? "-" : FMT.format(p.getPaidAt().atZone(c.zone()))));
    }
}

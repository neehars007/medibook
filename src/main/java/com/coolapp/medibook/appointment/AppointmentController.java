package com.coolapp.medibook.appointment;

import com.coolapp.medibook.appointment.AppointmentDtos.*;
import com.coolapp.medibook.consultation.ConsultationDtos.VisitSummaryDto;
import com.coolapp.medibook.consultation.ConsultationService;
import com.coolapp.medibook.payment.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AppointmentController {

    private final BookingService booking;
    private final ConsultationService consultation;
    private final PaymentService payments;

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse hold(@Valid @RequestBody HoldRequest req) {
        return booking.createHold(req);
    }

    @DeleteMapping("/holds/{id}")
    public ResponseEntity<Void> release(@PathVariable UUID id) {
        booking.releaseHold(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@Valid @RequestBody BookRequest req) {
        return booking.book(req);
    }

    @GetMapping("/appointments/me")
    public List<AppointmentDto> mine(@RequestParam(defaultValue = "upcoming") String status) {
        return booking.myAppointments(status);
    }

    @GetMapping("/appointments/{id}")
    public AppointmentDto get(@PathVariable UUID id) {
        return booking.get(id);
    }

    @PatchMapping("/appointments/{id}/reschedule")
    public AppointmentDto reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest req) {
        return booking.reschedule(id, req.holdId());
    }

    @PostMapping("/appointments/{id}/cancel")
    public AppointmentDto cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest req) {
        return booking.cancel(id, req == null ? null : req.reason());
    }

    @GetMapping("/appointments/{id}/video-link")
    public VideoLinkDto videoLink(@PathVariable UUID id) {
        return booking.videoLink(id);
    }

    /** Finalized visit summary for the patient portal (/me/visits/:id). */
    @GetMapping("/appointments/{id}/visit-summary")
    public VisitSummaryDto visitSummary(@PathVariable UUID id) {
        return consultation.visitSummaryForPatient(id);
    }

    @GetMapping("/appointments/{id}/summary.pdf")
    public ResponseEntity<byte[]> summaryPdf(@PathVariable UUID id) {
        return pdf(consultation.summaryPdf(id), "visit-summary-" + id + ".pdf");
    }

    @GetMapping("/appointments/{id}/prescription.pdf")
    public ResponseEntity<byte[]> prescriptionPdf(@PathVariable UUID id) {
        return pdf(consultation.prescriptionPdf(id), "prescription-" + id + ".pdf");
    }

    @GetMapping("/appointments/{id}/receipt.pdf")
    public ResponseEntity<byte[]> receiptPdf(@PathVariable UUID id) {
        return pdf(payments.receiptPdf(booking.loadForView(id)), "receipt-" + id + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}

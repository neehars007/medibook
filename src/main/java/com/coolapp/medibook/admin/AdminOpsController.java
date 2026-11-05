package com.coolapp.medibook.admin;

import com.coolapp.medibook.admin.AdminDtos.AuditLogDto;
import com.coolapp.medibook.admin.AdminDtos.RefundRequest;
import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentDtos.AppointmentDto;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentViews;
import com.coolapp.medibook.audit.AuditLog;
import com.coolapp.medibook.audit.AuditLogRepository;
import com.coolapp.medibook.payment.Payment;
import com.coolapp.medibook.payment.PaymentService;
import com.coolapp.medibook.report.ReportService;
import com.coolapp.medibook.review.ReviewService;
import com.coolapp.medibook.review.ReviewStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Appointment search, review moderation, reports, audit log and refunds. No clinical data here. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOpsController {

    private final AppointmentRepository appointments;
    private final AppointmentViews views;
    private final ReviewService reviews;
    private final ReportService reports;
    private final AuditLogRepository auditLogs;
    private final PaymentService payments;

    @GetMapping("/appointments")
    @Transactional(readOnly = true)
    public Page<AppointmentDto> appointments(@RequestParam(required = false) Instant from,
                                             @RequestParam(required = false) Instant to,
                                             @RequestParam(required = false) UUID doctorId,
                                             @RequestParam(required = false) AppointmentStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        Specification<Appointment> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isFalse(root.<Boolean>get("deleted")));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.<Instant>get("startAt"), from));
            if (to != null) p.add(cb.lessThan(root.<Instant>get("startAt"), to));
            if (doctorId != null) p.add(cb.equal(root.get("doctorId"), doctorId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(new Predicate[0]));
        };
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "startAt"));
        Page<Appointment> result = appointments.findAll(spec, pageable);
        return new PageImpl<>(views.many(result.getContent()), pageable, result.getTotalElements());
    }

    // ----- Reviews -----

    @GetMapping("/reviews")
    public Page<ReviewService.AdminReviewDto> reviews(@RequestParam(defaultValue = "PENDING") ReviewStatus status,
                                                      @RequestParam(defaultValue = "0") int page) {
        return reviews.byStatus(status, PageRequest.of(page, 25));
    }

    @PatchMapping("/reviews/{id}")
    public ReviewService.AdminReviewDto moderate(@PathVariable UUID id, @Valid @RequestBody ReviewService.ModerationRequest req) {
        return reviews.moderate(id, req.status());
    }

    // ----- Reports -----

    @GetMapping("/reports/summary")
    public Map<String, Object> summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var r = reports.defaultRange(from, to);
        return Map.of("today", reports.today(),
                "patients", reports.patients(r.from(), r.to()),
                "heatmap", reports.heatmap(r.from(), r.to()),
                "range", r);
    }

    @GetMapping("/reports/doctors")
    public List<ReportService.DoctorStats> doctorReport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var r = reports.defaultRange(from, to);
        return reports.doctors(r.from(), r.to());
    }

    @GetMapping("/reports/revenue")
    public ReportService.Revenue revenue(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var r = reports.defaultRange(from, to);
        return reports.revenue(r.from(), r.to());
    }

    @GetMapping("/reports/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var r = reports.defaultRange(from, to);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"appointments-" + r.from() + "-to-" + r.to() + ".csv\"")
                .body(reports.exportCsv(r.from(), r.to()).getBytes(StandardCharsets.UTF_8));
    }

    // ----- Audit -----

    @GetMapping("/audit-logs")
    public Page<AuditLogDto> auditLogs(@RequestParam(required = false) String entityType,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200));
        Page<AuditLog> result = entityType == null ? auditLogs.findAllByOrderByCreatedAtDesc(pageable)
                : auditLogs.findByEntityTypeOrderByCreatedAtDesc(entityType, pageable);
        return result.map(a -> new AuditLogDto(a.getId(), a.getActorId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getDetails(), a.getIpAddress(), a.getCreatedAt()));
    }

    // ----- Payments -----

    @PostMapping("/payments/{id}/refund")
    public Map<String, Object> refund(@PathVariable UUID id, @Valid @RequestBody(required = false) RefundRequest req) {
        Payment p = payments.adminRefund(id, req == null ? null : req.amount());
        return Map.of("paymentId", p.getId(), "status", p.getStatus(), "refundedAmount", p.getRefundedAmount());
    }
}

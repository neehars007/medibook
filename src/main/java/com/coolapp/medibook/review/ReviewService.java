package com.coolapp.medibook.review;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientAccessService;
import com.coolapp.medibook.patient.PatientRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Reviews are moderated before publishing: medical reviews often contain sensitive details. */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviews;
    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final PatientAccessService access;
    private final AuditService audit;

    public record ReviewRequest(@NotNull UUID appointmentId, @Min(1) @Max(5) int rating, @Size(max = 2000) String comment) {}

    public record PublicReviewDto(UUID id, int rating, String comment, String reviewerInitials, Instant createdAt) {}

    public record AdminReviewDto(UUID id, UUID appointmentId, UUID doctorId, int rating, String comment,
                                 ReviewStatus status, Instant createdAt) {}

    public record ModerationRequest(@NotNull ReviewStatus status) {}

    @Transactional
    public AdminReviewDto create(ReviewRequest req) {
        Appointment a = appointments.findById(req.appointmentId()).orElseThrow(() -> ApiException.notFound("Appointment"));
        Patient p = access.load(a.getPatientId());
        if (!access.isSelfOrGuardian(CurrentUser.get(), p)) {
            throw ApiException.forbidden("Not your appointment");
        }
        if (a.getStatus() != AppointmentStatus.COMPLETED) {
            throw ApiException.conflict("You can review a doctor after a completed visit");
        }
        if (reviews.existsByAppointmentId(a.getId())) {
            throw ApiException.conflict("You already reviewed this visit");
        }
        Review r = new Review();
        r.setAppointmentId(a.getId());
        r.setPatientId(a.getPatientId());
        r.setDoctorId(a.getDoctorId());
        r.setRating(req.rating());
        r.setComment(req.comment());
        reviews.save(r);
        audit.log("REVIEW_SUBMITTED", "Review", r.getId(), null);
        return admin(r);
    }

    @Transactional(readOnly = true)
    public Page<PublicReviewDto> approvedForDoctor(UUID doctorId, Pageable pageable) {
        return reviews.findByDoctorIdAndStatusOrderByCreatedAtDesc(doctorId, ReviewStatus.APPROVED, pageable)
                .map(r -> new PublicReviewDto(r.getId(), r.getRating(), r.getComment(),
                        patients.findById(r.getPatientId()).map(p -> initials(p.getFullName())).orElse("Patient"),
                        r.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public Page<AdminReviewDto> byStatus(ReviewStatus status, Pageable pageable) {
        return reviews.findByStatusOrderByCreatedAtAsc(status, pageable).map(ReviewService::admin);
    }

    @Transactional
    public AdminReviewDto moderate(UUID id, ReviewStatus status) {
        if (status == ReviewStatus.PENDING) {
            throw ApiException.badRequest("Choose APPROVED or REJECTED");
        }
        Review r = reviews.findById(id).orElseThrow(() -> ApiException.notFound("Review"));
        r.setStatus(status);
        audit.log("REVIEW_" + status, "Review", id, null);
        return admin(r);
    }

    private static AdminReviewDto admin(Review r) {
        return new AdminReviewDto(r.getId(), r.getAppointmentId(), r.getDoctorId(), r.getRating(), r.getComment(),
                r.getStatus(), r.getCreatedAt());
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

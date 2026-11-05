package com.coolapp.medibook.appointment;

import com.coolapp.medibook.common.BaseEntity;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.doctor.ConsultationMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "booked_by_user_id")
    private UUID bookedByUserId;
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "appointment_type_id", nullable = false)
    private UUID appointmentTypeId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at", nullable = false)
    private Instant endAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsultationMode mode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentSource source = AppointmentSource.ONLINE;
    @Column(name = "token_number")
    private Integer tokenNumber;
    @Column(name = "checked_in_at")
    private Instant checkedInAt;
    @Column(name = "consult_started_at")
    private Instant consultStartedAt;
    @Column(name = "consult_ended_at")
    private Instant consultEndedAt;
    @Column(name = "video_link")
    private String videoLink;
    @Column(name = "cancel_reason")
    private String cancelReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private CancelledBy cancelledBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false)
    private PaymentMode paymentMode;
    @Version
    private long version;

    /** Enforces the status machine; illegal moves become HTTP 409. */
    public AppointmentStatus transitionTo(AppointmentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw ApiException.conflict("Cannot move appointment from " + status + " to " + next);
        }
        AppointmentStatus previous = status;
        status = next;
        return previous;
    }
}

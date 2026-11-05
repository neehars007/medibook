package com.coolapp.medibook.appointment;

import com.coolapp.medibook.doctor.ConsultationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AppointmentDtos {
    private AppointmentDtos() {}

    public record HoldRequest(@NotNull UUID doctorId, @NotNull UUID typeId, @NotNull Instant startAt,
                              @NotNull ConsultationMode mode, UUID patientId) {}

    public record HoldResponse(UUID holdId, UUID doctorId, Instant startAt, Instant endAt, ConsultationMode mode, Instant expiresAt) {
        public static HoldResponse from(Hold h) {
            return new HoldResponse(h.id(), h.doctorId(), h.startAt(), h.endAt(), h.mode(), h.expiresAt());
        }
    }

    public record IntakeFormRequest(@Size(max = 2000) String reason, @Size(max = 4000) String symptoms,
                                    @Size(max = 200) String durationOfSymptoms, @Size(max = 2000) String currentMeds,
                                    boolean allergiesConfirmed, @Size(max = 4000) String extraNotes) {}

    public record IntakeFormDto(String reason, String symptoms, String durationOfSymptoms, String currentMeds,
                                boolean allergiesConfirmed, String extraNotes) {
        public static IntakeFormDto from(IntakeForm f) {
            return f == null ? null : new IntakeFormDto(f.getReason(), f.getSymptoms(), f.getDurationOfSymptoms(),
                    f.getCurrentMeds(), f.isAllergiesConfirmed(), f.getExtraNotes());
        }
    }

    public record BookRequest(@NotNull UUID holdId, @NotNull UUID patientId, @Valid IntakeFormRequest intakeForm,
                              PaymentMode paymentMode) {}

    public record BookingResponse(UUID appointmentId, AppointmentStatus status, Instant startAt, Instant endAt,
                                  String doctorName, ConsultationMode mode, PaymentMode paymentMode, BigDecimal amount,
                                  String paymentClientSecret, String clinicName, String clinicAddress, String mapUrl,
                                  boolean videoVisit) {}

    public record AppointmentDto(UUID id, UUID patientId, String patientName, UUID doctorId, String doctorName,
                                 UUID appointmentTypeId, String typeName, Instant startAt, Instant endAt,
                                 ConsultationMode mode, AppointmentStatus status, AppointmentSource source,
                                 Integer tokenNumber, PaymentMode paymentMode, String paymentStatus,
                                 CancelledBy cancelledBy, String cancelReason, String roomName,
                                 Instant checkedInAt, boolean hasVisitSummary) {}

    public record RescheduleRequest(@NotNull UUID holdId) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record VideoLinkDto(String url, Instant joinableFrom, Instant joinableUntil) {}

    public record ReceptionBookRequest(@NotNull UUID patientId, @NotNull UUID doctorId, @NotNull UUID typeId,
                                       @NotNull Instant startAt, @NotNull ConsultationMode mode,
                                       @Valid IntakeFormRequest intakeForm) {}

    public record WalkInRequest(@NotNull UUID patientId, @NotNull UUID typeId) {}
}

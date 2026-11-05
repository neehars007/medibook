package com.coolapp.medibook.doctor;

import com.coolapp.medibook.patient.Gender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class DoctorDtos {
    private DoctorDtos() {}

    public record DoctorCard(UUID id, String fullName, Gender gender, UUID specialtyId, String specialtyName,
                             String qualifications, int experienceYears, List<String> languages, String bio,
                             BigDecimal consultationFee, String photoUrl, Double rating, long reviewCount,
                             Instant nextAvailableSlot, boolean active) {}

    public record SessionRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                                 UUID roomId, @NotNull ConsultationMode mode) {}

    public record SessionDto(UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, UUID roomId,
                             ConsultationMode mode) {
        public static SessionDto from(DoctorSession s) {
            return new SessionDto(s.getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getRoomId(), s.getMode());
        }
    }

    public record LeaveRequest(@NotNull Instant startAt, @NotNull Instant endAt, String reason, LeaveKind kind) {}

    public record LeaveDto(UUID id, Instant startAt, Instant endAt, String reason, LeaveKind kind) {
        public static LeaveDto from(DoctorLeave l) {
            return new LeaveDto(l.getId(), l.getStartAt(), l.getEndAt(), l.getReason(), l.getKind());
        }
    }

    public record DoctorProfileRequest(@NotBlank String fullName, Gender gender, @NotNull UUID specialtyId,
                                       String qualifications, @Min(0) @Max(70) int experienceYears, String languages,
                                       String bio, @NotBlank String licenseNo,
                                       @NotNull @DecimalMin("0.00") BigDecimal consultationFee, String photoUrl,
                                       Boolean active) {}

    public record DoctorInviteRequest(@NotBlank @Email String email, String phone, @NotNull DoctorProfileRequest profile) {}
}

package com.coolapp.medibook.clinic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class ClinicDtos {
    private ClinicDtos() {}

    public record ClinicProfileRequest(@NotBlank String name, String address, String phone, @NotBlank String timezone,
                                       String logoUrl, String about, String emergencyNotice) {}

    public record ClinicPolicyRequest(
            @Min(0) @Max(10080) int minNoticeMinutes,
            @Min(1) @Max(365) int maxAdvanceDays,
            @Min(0) @Max(168) int cancellationCutoffHours,
            @NotNull PaymentPolicy paymentPolicy,
            @Min(1) @Max(20) int noShowPrepayThreshold,
            @Min(0) @Max(60) int bufferMinutes,
            @Min(5) @Max(60) int slotGranularityMinutes) {}

    public record HoursRequest(@NotNull DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {}

    public record HolidayRequest(@NotNull LocalDate date, @NotBlank String name) {}

    public record ClinicDto(UUID id, String name, String address, String phone, String timezone, String logoUrl,
                            String about, String emergencyNotice, int minNoticeMinutes, int maxAdvanceDays,
                            int cancellationCutoffHours, PaymentPolicy paymentPolicy, int noShowPrepayThreshold,
                            int bufferMinutes, int slotGranularityMinutes) {
        public static ClinicDto from(Clinic c) {
            return new ClinicDto(c.getId(), c.getName(), c.getAddress(), c.getPhone(), c.getTimezone(), c.getLogoUrl(),
                    c.getAbout(), c.getEmergencyNotice(), c.getMinNoticeMinutes(), c.getMaxAdvanceDays(),
                    c.getCancellationCutoffHours(), c.getPaymentPolicy(), c.getNoShowPrepayThreshold(),
                    c.getBufferMinutes(), c.getSlotGranularityMinutes());
        }
    }

    public record SpecialtyDto(UUID id, @NotBlank String name, String description, String icon) {
        public static SpecialtyDto from(Specialty s) {
            return new SpecialtyDto(s.getId(), s.getName(), s.getDescription(), s.getIcon());
        }
    }
}

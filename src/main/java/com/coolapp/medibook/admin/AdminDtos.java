package com.coolapp.medibook.admin;

import com.coolapp.medibook.auth.Role;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.room.RoomType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record AppointmentTypeRequest(@NotNull UUID specialtyId, @NotBlank String name,
                                         @Min(5) @Max(240) int durationMinutes, @DecimalMin("0.00") BigDecimal fee,
                                         RoomType requiresRoomType, boolean followUp, Integer followUpWindowDays,
                                         ConsultationMode mode, boolean intakeRequired, Boolean active) {}

    public record StaffInviteRequest(@NotBlank @Email String email, String phone, @NotNull Role role) {}

    public record StaffDto(UUID id, String email, String phone, Role role, boolean active, boolean totpEnabled, Instant createdAt) {}

    public record RefundRequest(@DecimalMin("0.01") BigDecimal amount) {}

    public record AuditLogDto(UUID id, UUID actorId, String action, String entityType, String entityId, String details,
                              String ipAddress, Instant createdAt) {}
}

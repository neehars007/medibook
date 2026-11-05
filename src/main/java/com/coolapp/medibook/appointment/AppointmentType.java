package com.coolapp.medibook.appointment;

import com.coolapp.medibook.common.BaseEntity;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.room.RoomType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "appointment_type")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentType extends BaseEntity {
    @Column(name = "specialty_id", nullable = false)
    private UUID specialtyId;
    @Column(nullable = false)
    private String name;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    /** Null means "use the doctor's consultation fee". Zero means free (e.g. follow-up). */
    private BigDecimal fee;
    @Enumerated(EnumType.STRING)
    @Column(name = "requires_room_type")
    private RoomType requiresRoomType;
    @Column(name = "is_follow_up", nullable = false)
    private boolean followUp;
    @Column(name = "follow_up_window_days")
    private Integer followUpWindowDays;
    /** Null means either mode. */
    @Enumerated(EnumType.STRING)
    private ConsultationMode mode;
    @Column(name = "intake_required", nullable = false)
    private boolean intakeRequired;
    @Column(nullable = false)
    private boolean active = true;

    public boolean supports(ConsultationMode m) {
        return mode == null || mode == m;
    }
}

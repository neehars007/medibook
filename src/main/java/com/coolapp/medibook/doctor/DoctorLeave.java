package com.coolapp.medibook.doctor;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_leave")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class DoctorLeave extends BaseEntity {
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at", nullable = false)
    private Instant endAt;
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveKind kind = LeaveKind.LEAVE;
}

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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/** A recurring weekly working block, e.g. Monday 09:00-13:00 in Room 2, in person. */
@Entity
@Table(name = "doctor_session")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class DoctorSession extends BaseEntity {
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "room_id")
    private UUID roomId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsultationMode mode;
}

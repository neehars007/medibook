package com.coolapp.medibook.clinic;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "clinic_hours")
@Getter
@Setter
@NoArgsConstructor
public class ClinicHours extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;
    @Column(name = "open_time")
    private LocalTime openTime;
    @Column(name = "close_time")
    private LocalTime closeTime;
    @Column(nullable = false)
    private boolean closed;
}

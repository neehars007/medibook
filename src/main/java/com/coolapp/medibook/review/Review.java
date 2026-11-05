package com.coolapp.medibook.review;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
public class Review extends BaseEntity {
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Column(nullable = false)
    private int rating;
    private String comment;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;
}

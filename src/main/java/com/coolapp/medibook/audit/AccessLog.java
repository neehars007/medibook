package com.coolapp.medibook.audit;

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

/** One row per view of clinical data (history, notes, documents). Patients can see these. */
@Entity
@Table(name = "access_log")
@Getter
@Setter
@NoArgsConstructor
public class AccessLog extends BaseEntity {
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessResource resource;
    @Column(name = "resource_id")
    private String resourceId;
}

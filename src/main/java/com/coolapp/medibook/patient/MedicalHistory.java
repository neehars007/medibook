package com.coolapp.medibook.patient;

import com.coolapp.medibook.common.BaseEntity;
import com.coolapp.medibook.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "medical_history")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class MedicalHistory extends BaseEntity {
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedicalHistoryType type;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String description;
    private LocalDate since;
    @Column(nullable = false)
    private boolean active = true;
}

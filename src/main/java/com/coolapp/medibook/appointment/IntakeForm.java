package com.coolapp.medibook.appointment;

import com.coolapp.medibook.common.BaseEntity;
import com.coolapp.medibook.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "intake_form")
@Getter
@Setter
@NoArgsConstructor
public class IntakeForm extends BaseEntity {
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;
    @Convert(converter = EncryptedStringConverter.class)
    private String reason;
    @Convert(converter = EncryptedStringConverter.class)
    private String symptoms;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "duration_of_symptoms")
    private String durationOfSymptoms;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "current_meds")
    private String currentMeds;
    @Column(name = "allergies_confirmed", nullable = false)
    private boolean allergiesConfirmed;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "extra_notes")
    private String extraNotes;
}

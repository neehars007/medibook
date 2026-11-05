package com.coolapp.medibook.consultation;

import com.coolapp.medibook.common.BaseEntity;
import com.coolapp.medibook.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Clinical note. Once {@code finalizedAt} is set it is immutable; changes become addendums. */
@Entity
@Table(name = "visit_note")
@Getter
@Setter
@NoArgsConstructor
public class VisitNote extends BaseEntity {
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "chief_complaint")
    private String chiefComplaint;
    @Convert(converter = EncryptedStringConverter.class)
    private String findings;
    /** JSON of {@link ConsultationDtos.Vitals}, encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "vitals")
    private String vitalsJson;
    @Convert(converter = EncryptedStringConverter.class)
    private String diagnosis;
    @Column(name = "icd_code")
    private String icdCode;
    @Convert(converter = EncryptedStringConverter.class)
    private String advice;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "recommended_tests")
    private String recommendedTests;
    @Column(name = "follow_up_in_days")
    private Integer followUpInDays;
    @Column(name = "finalized_at")
    private Instant finalizedAt;
    @Column(name = "summary_pdf_key")
    private String summaryPdfKey;

    public boolean isFinalized() {
        return finalizedAt != null;
    }
}

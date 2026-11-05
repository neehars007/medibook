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
import java.time.Period;
import java.util.UUID;

/**
 * A person who receives care. {@code userId} is null for walk-ins without an account and for
 * dependents; dependents point at their guardian via {@code guardianPatientId}.
 */
@Entity
@Table(name = "patient")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends BaseEntity {
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "guardian_patient_id")
    private UUID guardianPatientId;
    private String relationship;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(nullable = false)
    private LocalDate dob;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;
    private String phone;
    private String address;
    @Column(name = "emergency_contact_name")
    private String emergencyContactName;
    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;
    @Column(name = "blood_group")
    private String bloodGroup;
    @Column(name = "insurance_provider")
    private String insuranceProvider;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "insurance_policy_no")
    private String insurancePolicyNo;

    public int ageOn(LocalDate date) {
        return Period.between(dob, date).getYears();
    }
}

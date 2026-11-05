package com.coolapp.medibook.doctor;

import com.coolapp.medibook.common.BaseEntity;
import com.coolapp.medibook.patient.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "doctor")
@Getter
@Setter
@NoArgsConstructor
public class Doctor extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Enumerated(EnumType.STRING)
    private Gender gender;
    @Column(name = "specialty_id", nullable = false)
    private UUID specialtyId;
    private String qualifications;
    @Column(name = "experience_years", nullable = false)
    private int experienceYears;
    /** Comma-separated, e.g. "English,Spanish". */
    private String languages;
    private String bio;
    @Column(name = "license_no", nullable = false)
    private String licenseNo;
    @Column(name = "consultation_fee", nullable = false)
    private BigDecimal consultationFee = BigDecimal.ZERO;
    @Column(name = "photo_url")
    private String photoUrl;
    @Column(nullable = false)
    private boolean active = true;

    public List<String> languageList() {
        return languages == null || languages.isBlank() ? List.of()
                : Arrays.stream(languages.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}

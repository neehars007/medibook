package com.coolapp.medibook.prescription;

import com.coolapp.medibook.common.BaseEntity;
import com.coolapp.medibook.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescription_item")
@Getter
@Setter
@NoArgsConstructor
public class PrescriptionItem extends BaseEntity {
    @Column(nullable = false)
    private int position;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String medicine;
    @Convert(converter = EncryptedStringConverter.class)
    private String dosage;
    @Convert(converter = EncryptedStringConverter.class)
    private String frequency;
    @Column(name = "duration_days")
    private Integer durationDays;
    @Convert(converter = EncryptedStringConverter.class)
    private String instructions;
}

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

import java.util.UUID;

@Entity
@Table(name = "visit_note_addendum")
@Getter
@Setter
@NoArgsConstructor
public class VisitNoteAddendum extends BaseEntity {
    @Column(name = "visit_note_id", nullable = false)
    private UUID visitNoteId;
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String text;
}

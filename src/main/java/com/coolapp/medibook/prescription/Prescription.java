package com.coolapp.medibook.prescription;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "prescription")
@Getter
@Setter
@NoArgsConstructor
public class Prescription extends BaseEntity {
    @Column(name = "visit_note_id", nullable = false)
    private UUID visitNoteId;
    @Column(name = "pdf_key")
    private String pdfKey;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "prescription_id", nullable = false)
    @OrderBy("position ASC")
    private List<PrescriptionItem> items = new ArrayList<>();
}

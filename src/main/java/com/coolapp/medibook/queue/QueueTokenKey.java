package com.coolapp.medibook.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class QueueTokenKey implements Serializable {
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Column(name = "day", nullable = false)
    private LocalDate day;
}

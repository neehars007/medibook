package com.coolapp.medibook.appointment;

import com.coolapp.medibook.doctor.ConsultationMode;

import java.time.Instant;
import java.util.UUID;

/** A 10-minute reservation of a slot while the patient fills the intake form and pays. */
public record Hold(UUID id, UUID userId, UUID doctorId, UUID roomId, UUID typeId,
                   Instant startAt, Instant endAt, ConsultationMode mode, Instant expiresAt) {}

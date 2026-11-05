package com.coolapp.medibook.availability;

import com.coolapp.medibook.doctor.ConsultationMode;

import java.time.Instant;
import java.util.UUID;

public record Slot(UUID doctorId, Instant startAt, Instant endAt, ConsultationMode mode, UUID roomId) {}

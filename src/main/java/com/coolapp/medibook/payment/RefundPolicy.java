package com.coolapp.medibook.payment;

import com.coolapp.medibook.appointment.CancelledBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Full refund if the patient cancels before the cutoff, or whenever the clinic/doctor cancels.
 * No refund for patient cancellations after the cutoff.
 */
public final class RefundPolicy {

    private RefundPolicy() {}

    public static BigDecimal refundAmount(BigDecimal paid, CancelledBy by, Instant start, Instant now, int cutoffHours) {
        if (paid == null || paid.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (by != CancelledBy.PATIENT) {
            return paid;
        }
        return isBeforeCutoff(start, now, cutoffHours) ? paid : BigDecimal.ZERO;
    }

    public static boolean isBeforeCutoff(Instant start, Instant now, int cutoffHours) {
        return now.isBefore(start.minus(Duration.ofHours(cutoffHours)));
    }
}

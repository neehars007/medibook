package com.coolapp.medibook.payment;

import com.coolapp.medibook.appointment.CancelledBy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyTest {

    private static final BigDecimal PAID = new BigDecimal("120.00");
    private static final Instant START = Instant.parse("2026-11-10T15:00:00Z");

    @Test
    void patientBeforeCutoffGetsFullRefund() {
        Instant now = START.minus(Duration.ofHours(5));
        assertThat(RefundPolicy.refundAmount(PAID, CancelledBy.PATIENT, START, now, 4)).isEqualByComparingTo(PAID);
    }

    @Test
    void patientAfterCutoffGetsNothing() {
        Instant now = START.minus(Duration.ofHours(3));
        assertThat(RefundPolicy.refundAmount(PAID, CancelledBy.PATIENT, START, now, 4)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void clinicOrDoctorCancellationAlwaysFull() {
        Instant now = START.minus(Duration.ofMinutes(10));
        assertThat(RefundPolicy.refundAmount(PAID, CancelledBy.CLINIC, START, now, 4)).isEqualByComparingTo(PAID);
        assertThat(RefundPolicy.refundAmount(PAID, CancelledBy.DOCTOR_EMERGENCY, START, now, 4)).isEqualByComparingTo(PAID);
    }

    @Test
    void nothingPaidNothingRefunded() {
        assertThat(RefundPolicy.refundAmount(BigDecimal.ZERO, CancelledBy.CLINIC, START, START, 4)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(RefundPolicy.refundAmount(null, CancelledBy.CLINIC, START, START, 4)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

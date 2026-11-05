package com.coolapp.medibook.appointment;

import com.coolapp.medibook.clinic.PaymentPolicy;
import com.coolapp.medibook.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingRulesTest {

    private static final BigDecimal FEE = new BigDecimal("100.00");

    @Test
    void freeTypesSkipPayment() {
        assertThat(BookingRules.decidePaymentMode(BigDecimal.ZERO, PaymentMode.ONLINE, PaymentPolicy.EITHER, 0, 2, false))
                .isEqualTo(PaymentMode.FREE);
    }

    @Test
    void payAtClinicAllowedWhenPolicyPermits() {
        assertThat(BookingRules.decidePaymentMode(FEE, PaymentMode.PAY_AT_CLINIC, PaymentPolicy.EITHER, 0, 2, false))
                .isEqualTo(PaymentMode.PAY_AT_CLINIC);
    }

    @Test
    void repeatNoShowsMustPrepay() {
        assertThatThrownBy(() -> BookingRules.decidePaymentMode(FEE, PaymentMode.PAY_AT_CLINIC, PaymentPolicy.EITHER, 2, 2, false))
                .isInstanceOf(ApiException.class).hasMessageContaining("Prepayment");
        assertThat(BookingRules.decidePaymentMode(FEE, null, PaymentPolicy.PAY_AT_CLINIC_ONLY, 3, 2, false))
                .isEqualTo(PaymentMode.ONLINE);
    }

    @Test
    void onlineOnlyPolicy() {
        assertThatThrownBy(() -> BookingRules.decidePaymentMode(FEE, PaymentMode.PAY_AT_CLINIC, PaymentPolicy.ONLINE_ONLY, 0, 2, false))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void staffBookingsAreCollectedAtCounter() {
        assertThat(BookingRules.decidePaymentMode(FEE, PaymentMode.ONLINE, PaymentPolicy.ONLINE_ONLY, 5, 2, true))
                .isEqualTo(PaymentMode.PAY_AT_CLINIC);
    }
}

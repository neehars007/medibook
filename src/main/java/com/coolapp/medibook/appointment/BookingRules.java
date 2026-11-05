package com.coolapp.medibook.appointment;

import com.coolapp.medibook.clinic.PaymentPolicy;
import com.coolapp.medibook.common.exception.ApiException;

import java.math.BigDecimal;

/** Pure payment-mode rules applied at booking time (unit-tested). */
public final class BookingRules {

    private BookingRules() {}

    public static PaymentMode decidePaymentMode(BigDecimal fee, PaymentMode requested, PaymentPolicy policy,
                                                long noShows, int noShowThreshold, boolean staffBooking) {
        if (fee == null || fee.signum() <= 0) {
            return PaymentMode.FREE; // free follow-ups skip payment
        }
        if (staffBooking) {
            return PaymentMode.PAY_AT_CLINIC; // front desk collects at the counter
        }
        boolean prepayRequired = noShows >= noShowThreshold;
        if (requested == null) {
            if (prepayRequired || policy != PaymentPolicy.PAY_AT_CLINIC_ONLY) {
                return PaymentMode.ONLINE;
            }
            return PaymentMode.PAY_AT_CLINIC;
        }
        return switch (requested) {
            case FREE -> throw ApiException.badRequest("This appointment type is not free");
            case ONLINE -> {
                if (policy == PaymentPolicy.PAY_AT_CLINIC_ONLY && !prepayRequired) {
                    throw ApiException.badRequest("This clinic only accepts payment at the clinic");
                }
                yield PaymentMode.ONLINE;
            }
            case PAY_AT_CLINIC -> {
                if (prepayRequired) {
                    throw ApiException.badRequest("Prepayment is required because of previous missed appointments");
                }
                if (policy == PaymentPolicy.ONLINE_ONLY) {
                    throw ApiException.badRequest("This clinic requires online prepayment");
                }
                yield PaymentMode.PAY_AT_CLINIC;
            }
        };
    }
}

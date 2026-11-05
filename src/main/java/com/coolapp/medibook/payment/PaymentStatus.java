package com.coolapp.medibook.payment;

public enum PaymentStatus {
    PENDING, SUCCEEDED, FAILED, REFUNDED, PARTIALLY_REFUNDED;

    public boolean wasCollected() {
        return this == SUCCEEDED || this == REFUNDED || this == PARTIALLY_REFUNDED;
    }
}

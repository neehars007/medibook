package com.coolapp.medibook.payment;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;
    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;
    @Column(nullable = false)
    private BigDecimal amount;
    /** Null until collected for pay-at-clinic bookings. */
    @Enumerated(EnumType.STRING)
    private PaymentMethod mode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    @Column(name = "refunded_amount", nullable = false)
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "receipt_pdf_key")
    private String receiptPdfKey;

    public BigDecimal netAmount() {
        return amount.subtract(refundedAmount == null ? BigDecimal.ZERO : refundedAmount);
    }
}

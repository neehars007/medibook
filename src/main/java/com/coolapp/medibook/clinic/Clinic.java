package com.coolapp.medibook.clinic;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZoneId;

/** Single-clinic deployment: exactly one row. Holds profile and booking policies. */
@Entity
@Table(name = "clinic")
@Getter
@Setter
@NoArgsConstructor
public class Clinic extends BaseEntity {
    @Column(nullable = false)
    private String name;
    private String address;
    private String phone;
    @Column(nullable = false)
    private String timezone;
    @Column(name = "logo_url")
    private String logoUrl;
    private String about;
    @Column(name = "emergency_notice")
    private String emergencyNotice;

    // --- Policies ---
    @Column(name = "min_notice_minutes", nullable = false)
    private int minNoticeMinutes = 60;
    @Column(name = "max_advance_days", nullable = false)
    private int maxAdvanceDays = 30;
    @Column(name = "cancellation_cutoff_hours", nullable = false)
    private int cancellationCutoffHours = 4;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_policy", nullable = false)
    private PaymentPolicy paymentPolicy = PaymentPolicy.EITHER;
    @Column(name = "no_show_prepay_threshold", nullable = false)
    private int noShowPrepayThreshold = 2;
    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes = 5;
    @Column(name = "slot_granularity_minutes", nullable = false)
    private int slotGranularityMinutes = 10;

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }
}

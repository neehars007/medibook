package com.coolapp.medibook.notification;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {
    @Column(name = "appointment_id")
    private UUID appointmentId;
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel = NotificationChannel.EMAIL;
    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;
    @Column(name = "last_error")
    private String lastError;
}

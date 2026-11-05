package com.coolapp.medibook.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NotificationStatus status, Instant now, Pageable page);

    List<Notification> findByAppointmentIdAndStatusAndTypeIn(UUID appointmentId, NotificationStatus status, Collection<NotificationType> types);
}

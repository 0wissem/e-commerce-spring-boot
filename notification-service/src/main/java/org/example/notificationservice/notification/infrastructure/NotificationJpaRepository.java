package org.example.notificationservice.notification.infrastructure;

import org.example.notificationservice.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationJpaRepository extends JpaRepository<Notification, String> {

    Optional<Notification> findByEventId(String eventId);
}

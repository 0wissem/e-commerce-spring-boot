package org.example.notificationservice.notification.application.dto;

import org.example.notificationservice.notification.domain.NotificationStatus;
import org.example.notificationservice.notification.domain.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String eventId,
        NotificationType type,
        NotificationStatus status,
        String productId,
        String productName,
        int stockQuantity,
        int threshold,
        String recipient,
        int attempts,
        Instant occurredAt,
        Instant createdAt,
        Instant sentAt) {
}

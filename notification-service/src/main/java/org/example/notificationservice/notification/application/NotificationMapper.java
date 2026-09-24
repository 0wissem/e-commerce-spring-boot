package org.example.notificationservice.notification.application;

import org.example.notificationservice.notification.application.dto.NotificationResponse;
import org.example.notificationservice.notification.domain.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventId(), n.getType(), n.getStatus(),
                n.getProductId(), n.getProductName(), n.getStockQuantity(), n.getThreshold(),
                n.getRecipient(), n.getAttempts(), n.getOccurredAt(), n.getCreatedAt(), n.getSentAt());
    }
}

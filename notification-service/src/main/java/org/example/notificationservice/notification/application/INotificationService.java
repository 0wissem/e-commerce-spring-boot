package org.example.notificationservice.notification.application;

import org.example.notificationservice.notification.application.dto.NotificationResponse;
import org.example.notificationservice.notification.application.dto.StockLowEvent;
import org.example.notificationservice.shared.response.PageResponse;

public interface INotificationService {

    HandlingOutcome handleStockLow(StockLowEvent event);

    PageResponse<NotificationResponse> getAll(int page, int size);

    NotificationResponse getById(String id);
}

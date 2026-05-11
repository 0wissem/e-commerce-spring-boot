package org.example.springboot0.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LowStockAlertUseCase {

    private static final Logger log = LoggerFactory.getLogger(LowStockAlertUseCase.class);

    private final INotificationService notificationService;

    @Value("${app.notification.low-stock-threshold}")
    private int threshold;

    public LowStockAlertUseCase(INotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void handleStockUpdated(String productName, int currentStock) {
        if (currentStock < threshold) {
            notificationService.sendLowStockAlert(productName, currentStock, threshold);
            log.info("Low stock alert triggered for product: {}", productName);
        }
    }
}

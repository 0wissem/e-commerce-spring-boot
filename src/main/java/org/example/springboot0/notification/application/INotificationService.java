package org.example.springboot0.notification.application;

public interface INotificationService {
    void sendLowStockAlert(String productName, int currentStock, int threshold);
}

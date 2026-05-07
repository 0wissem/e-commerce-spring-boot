package org.example.springboot0.notification.application;

public record StockUpdatedEvent(String productName, int stockQuantity) {}
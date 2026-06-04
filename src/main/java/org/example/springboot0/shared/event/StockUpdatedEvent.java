package org.example.springboot0.shared.event;

public record StockUpdatedEvent(String productId, String productName, int stockQuantity) {}
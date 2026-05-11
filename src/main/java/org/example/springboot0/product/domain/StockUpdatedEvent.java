package org.example.springboot0.product.domain;

public record StockUpdatedEvent(String productId, String productName, int stockQuantity) {}

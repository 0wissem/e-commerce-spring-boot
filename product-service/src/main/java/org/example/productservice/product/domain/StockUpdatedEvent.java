package org.example.productservice.product.domain;

public record StockUpdatedEvent(String productId, String productName, int stockQuantity) {}

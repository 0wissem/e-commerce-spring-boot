package org.example.productservice.product.infrastructure;

public record ProductEventPayload(
        String eventId,
        String eventType,
        String source,
        String productId,
        String name,
        double price,
        int stockQuantity
) {}
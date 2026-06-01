package org.example.productservice.product.infrastructure;

import java.util.List;

public record ProductEventPayload(
        String eventId,
        String eventType,
        String source,
        String productId,
        String name,
        double price,
        int stockQuantity,
        List<String> categoryNames
) {
    public ProductEventPayload {
        categoryNames = categoryNames == null ? List.of() : categoryNames;
    }
}
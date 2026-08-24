package org.example.productservice.product.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * BigDecimal serializes as a JSON number exactly like the previous double, so that change is
 * wire-compatible for existing clients. Every other field here is additive.
 */
public record ProductResponse(
        String id,
        String sku,
        String name,
        String brand,
        String description,
        BigDecimal price,
        BigDecimal finalPrice,
        String currency,
        int stockQuantity,
        Instant createdAt,
        Instant updatedAt,
        Set<CategoryInfo> categories
) {
    public record CategoryInfo(String id, String name) {}
}

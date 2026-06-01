package org.example.productservice.product.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductEventPayload(
        String eventId,
        String eventType,
        String source,
        String productId,
        String name,
        double price,
        int stockQuantity,
        List<CategoryDto> categories
) {
    public record CategoryDto(String id, String name) {}

    public ProductEventPayload {
        categories = categories == null ? List.of() : categories;
    }
}

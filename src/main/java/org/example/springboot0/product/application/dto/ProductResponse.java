package org.example.springboot0.product.application.dto;

import java.util.Set;

public record ProductResponse(
        String id,
        String name,
        double price,
        double finalPrice,
        int stockQuantity,
        Set<CategoryInfo> categories
) {
    public record CategoryInfo(String id, String name) {}
}
package org.example.springboot0.product.application.dto;

public record ProductSearchRequest(
        String query,
        Double minPrice,
        Double maxPrice,
        String categoryId,
        Boolean inStock,
        int page,
        int size
) {}
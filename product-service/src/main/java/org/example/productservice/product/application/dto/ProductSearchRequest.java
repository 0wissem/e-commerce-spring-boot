package org.example.productservice.product.application.dto;

public record ProductSearchRequest(
        String query,
        Double minPrice,
        Double maxPrice,
        String categoryId,
        Boolean inStock,
        int page,
        int size
) {}
package org.example.productservice.product.application.dto;

import java.math.BigDecimal;

public record ProductSearchRequest(
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String brand,
        String categoryId,
        Boolean inStock,
        int page,
        int size
) {}

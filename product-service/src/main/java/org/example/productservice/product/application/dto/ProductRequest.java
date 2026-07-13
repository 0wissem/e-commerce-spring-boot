package org.example.productservice.product.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ProductRequest(
        @NotBlank(message = "Name must not be blank") String name,
        @NotNull(message = "Price must not be null") @Positive(message = "Price must be positive") Double price,
        @NotNull(message = "Stock quantity must not be null") @Min(value = 0, message = "Stock quantity must be 0 or more") Integer stockQuantity,
        List<String> categoryIds
) {}
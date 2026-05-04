package org.example.springboot0.product.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ProductRequest(
        @NotBlank(message = "Name must not be blank") String name,
        @Positive(message = "Price must be positive") double price,
        @Min(value = 0, message = "Stock quantity must be 0 or more") int stockQuantity,
        List<String> categoryIds
) {}
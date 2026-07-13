package org.example.orderservice.order.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotBlank(message = "Product ID must not be blank") String productId,
        @NotNull(message = "Quantity must not be null") @Min(value = 1, message = "Quantity must be at least 1") Integer quantity
) {}

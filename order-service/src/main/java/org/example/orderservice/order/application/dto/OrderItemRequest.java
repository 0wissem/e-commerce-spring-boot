package org.example.orderservice.order.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderItemRequest(
        @NotBlank(message = "Product ID must not be blank") String productId,
        @Min(value = 1, message = "Quantity must be at least 1") int quantity
) {}

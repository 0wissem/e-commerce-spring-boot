package org.example.springboot0.order.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderRequest(
        @NotBlank(message = "Customer ID must not be blank") String customerId,
        @NotEmpty(message = "Order must contain at least one item") @Valid List<OrderItemRequest> items
) {}
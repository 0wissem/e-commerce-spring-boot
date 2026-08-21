package org.example.orderservice.order.application.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.example.orderservice.order.domain.ShippingAddress;


public record OrderRequest(
        @NotBlank(message = "Customer ID must not be blank") String customerId,
        @NotEmpty(message = "Order must contain at least one item") @Valid List<OrderItemRequest> items,

        // Optional and additive — existing clients that omit it keep working.
        ShippingAddress shippingAddress
) {}

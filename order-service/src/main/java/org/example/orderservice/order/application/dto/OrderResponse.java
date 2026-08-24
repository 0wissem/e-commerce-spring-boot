package org.example.orderservice.order.application.dto;

import org.example.orderservice.order.domain.OrderStatus;
import org.example.orderservice.order.domain.ShippingAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String orderNumber,
        String customerId,
        String customerName,
        OrderStatus status,
        BigDecimal totalPrice,
        String currency,
        ShippingAddress shippingAddress,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {}

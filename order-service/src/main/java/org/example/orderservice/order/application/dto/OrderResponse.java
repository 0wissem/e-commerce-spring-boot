package org.example.orderservice.order.application.dto;

import org.example.orderservice.order.domain.OrderStatus;

import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        String customerName,
        OrderStatus status,
        double totalPrice,
        List<OrderItemResponse> items
) {}

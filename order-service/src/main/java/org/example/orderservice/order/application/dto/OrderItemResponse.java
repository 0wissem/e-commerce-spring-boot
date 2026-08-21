package org.example.orderservice.order.application.dto;

import org.example.orderservice.order.domain.OrderProductSnapshot;

import java.math.BigDecimal;

public record OrderItemResponse(
        String id,
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        OrderProductSnapshot productSnapshot
) {}

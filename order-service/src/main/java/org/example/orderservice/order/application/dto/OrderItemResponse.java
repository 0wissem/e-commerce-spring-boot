package org.example.orderservice.order.application.dto;

import org.example.orderservice.order.domain.OrderProductSnapshot;

public record OrderItemResponse(String id, String productId, String productName, int quantity, double unitPrice, double subtotal, OrderProductSnapshot productSnapshot) {}
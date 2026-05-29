package org.example.springboot0.order.application.dto;

import org.example.springboot0.order.domain.OrderProductSnapshot;

public record OrderItemResponse(String id, String productId, String productName, int quantity, double unitPrice, double subtotal, OrderProductSnapshot productSnapshot) {}
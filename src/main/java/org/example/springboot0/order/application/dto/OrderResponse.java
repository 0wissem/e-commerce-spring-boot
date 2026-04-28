package org.example.springboot0.order.application.dto;

import org.example.springboot0.order.domain.OrderStatus;

import java.util.List;

public record OrderResponse(String id, String customerId, String customerName, OrderStatus status, List<OrderItemResponse> items, double totalPrice) {}
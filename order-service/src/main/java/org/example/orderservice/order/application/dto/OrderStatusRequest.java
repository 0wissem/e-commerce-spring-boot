package org.example.orderservice.order.application.dto;

import org.example.orderservice.order.domain.OrderStatus;

public record OrderStatusRequest(OrderStatus status) {}

package org.example.springboot0.order.application.dto;

import org.example.springboot0.order.domain.OrderStatus;

public record OrderStatusRequest(OrderStatus status) {}
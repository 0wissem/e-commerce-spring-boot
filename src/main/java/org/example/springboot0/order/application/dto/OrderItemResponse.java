package org.example.springboot0.order.application.dto;

public record OrderItemResponse(String id, String productId, String productName, int quantity, double unitPrice, double subtotal) {}
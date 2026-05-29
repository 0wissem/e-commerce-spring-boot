package org.example.springboot0.order.application;

import org.example.springboot0.order.application.dto.OrderItemResponse;
import org.example.springboot0.order.application.dto.OrderResponse;
import org.example.springboot0.order.domain.Order;
import org.example.springboot0.order.domain.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal(),
                item.getProductSnapshot()
        );
    }

    public OrderResponse toResponse(Order order, String customerName) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                customerName,
                order.getStatus(),
                items,
                order.getTotalPrice()
        );
    }
}
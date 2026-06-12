package org.example.orderservice.order.application;

import org.example.orderservice.order.application.dto.OrderItemResponse;
import org.example.orderservice.order.application.dto.OrderResponse;
import org.example.orderservice.order.domain.Order;
import org.example.orderservice.order.domain.OrderItem;
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
                item.getTotalPrice(),
                item.getProductSnapshot()
        );
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getStatus(),
                order.getTotalPrice(),
                items
        );
    }
}

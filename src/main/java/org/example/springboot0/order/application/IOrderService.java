package org.example.springboot0.order.application;

import org.example.springboot0.order.application.dto.OrderRequest;
import org.example.springboot0.order.application.dto.OrderResponse;
import org.example.springboot0.order.application.dto.OrderStatusRequest;

import java.util.List;

public interface IOrderService {
    List<OrderResponse> getAll();
    OrderResponse getById(String id);
    List<OrderResponse> getByCustomerId(String customerId);
    OrderResponse create(OrderRequest request);
    OrderResponse updateStatus(String id, OrderStatusRequest request);
    void delete(String id);
}
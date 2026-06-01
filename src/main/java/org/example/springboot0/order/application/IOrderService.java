package org.example.springboot0.order.application;

import org.example.springboot0.order.application.dto.OrderRequest;
import org.example.springboot0.order.application.dto.OrderResponse;
import org.example.springboot0.order.application.dto.OrderStatusRequest;
import org.example.springboot0.shared.response.PageResponse;

import java.util.List;

public interface IOrderService {
    List<OrderResponse> getAll();
    PageResponse<OrderResponse> getAll(int page, int size);
    OrderResponse getById(String id);
    List<OrderResponse> getByCustomerId(String customerId);
    OrderResponse create(OrderRequest request);
    OrderResponse updateStatus(String id, OrderStatusRequest request);
    void delete(String id);
}
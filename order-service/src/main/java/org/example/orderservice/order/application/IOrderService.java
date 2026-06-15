package org.example.orderservice.order.application;

import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.application.dto.OrderResponse;
import org.example.orderservice.order.application.dto.OrderStatusRequest;
import org.example.orderservice.shared.response.PageResponse;

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

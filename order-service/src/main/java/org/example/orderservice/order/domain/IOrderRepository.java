package org.example.orderservice.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IOrderRepository {
    List<Order> findAll();
    Page<Order> findAll(Pageable pageable);
    Optional<Order> findById(String id);
    List<Order> findByCustomerId(String customerId);

    /**
     * Keyset page of a customer's history, newest first. Pass a null cursor for the first page.
     * Returns at most {@code limit} orders.
     */
    List<Order> findCustomerHistoryPage(String customerId, Instant afterCreatedAt, String afterId, int limit);
    Order save(Order order);
    boolean existsById(String id);
    void deleteById(String id);
}

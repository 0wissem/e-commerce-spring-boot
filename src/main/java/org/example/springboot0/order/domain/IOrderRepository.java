package org.example.springboot0.order.domain;

import java.util.List;
import java.util.Optional;

public interface IOrderRepository {
    List<Order> findAll();
    Optional<Order> findById(String id);
    List<Order> findByCustomerId(String customerId);
    Order save(Order order);
    boolean existsById(String id);
    void deleteById(String id);
}
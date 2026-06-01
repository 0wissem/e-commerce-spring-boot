package org.example.springboot0.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IOrderRepository {
    List<Order> findAll();
    Page<Order> findAll(Pageable pageable);
    Optional<Order> findById(String id);
    List<Order> findByCustomerId(String customerId);
    Order save(Order order);
    boolean existsById(String id);
    void deleteById(String id);
}
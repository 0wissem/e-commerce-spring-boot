package org.example.springboot0.order.infrastructure;

import org.example.springboot0.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface OrderJpaRepository extends JpaRepository<Order, String> {
    List<Order> findByCustomerId(String customerId);
}
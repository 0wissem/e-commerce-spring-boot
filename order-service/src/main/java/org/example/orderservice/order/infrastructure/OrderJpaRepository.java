package org.example.orderservice.order.infrastructure;

import org.example.orderservice.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface OrderJpaRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerId(String customerId);

    // Load the items alongside the orders to avoid the N+1 query problem.
    @EntityGraph(attributePaths = {"orderItems"})
    @Override
    Page<Order> findAll(Pageable pageable);
}

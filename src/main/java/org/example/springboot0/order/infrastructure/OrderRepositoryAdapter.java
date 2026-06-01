package org.example.springboot0.order.infrastructure;

import org.example.springboot0.order.domain.IOrderRepository;
import org.example.springboot0.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepositoryAdapter implements IOrderRepository {

    private final OrderJpaRepository jpa;

    public OrderRepositoryAdapter(OrderJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Order> findAll() { return jpa.findAll(); }

    @Override
    public Page<Order> findAll(Pageable pageable) { return jpa.findAll(pageable); }

    @Override
    public Optional<Order> findById(String id) { return jpa.findById(id); }

    @Override
    public List<Order> findByCustomerId(String customerId) { return jpa.findByCustomerId(customerId); }

    @Override
    public Order save(Order order) { return jpa.save(order); }

    @Override
    public boolean existsById(String id) { return jpa.existsById(id); }

    @Override
    public void deleteById(String id) { jpa.deleteById(id); }
}
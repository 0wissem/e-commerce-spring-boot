package org.example.orderservice.order.application;

import org.example.orderservice.order.domain.IOrderRepository;
import org.example.orderservice.order.domain.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of order creation — and nothing else.
 *
 * WHY IT IS A SEPARATE BEAN:
 *
 * OrderService.create() used to be @Transactional and made every HTTP call inside that
 * boundary. A database connection was therefore held, unused, for the entire duration of two
 * or three network round trips. Under load the pool empties and the service stops — a slow
 * downstream turning into an outage here.
 *
 * The fix is to shrink the transaction to the writes alone. It cannot simply be a private
 * method on OrderService, because @Transactional is proxy-based: a self-invocation would
 * bypass the proxy and open no transaction at all. Calling an injected bean guarantees the
 * proxy is in the path — the same rule that governs @Cacheable and @Async.
 *
 * The transaction now covers one INSERT and its cascade, and opens only once every remote
 * call has already returned.
 */
@Service
public class OrderPersister {

    private final IOrderRepository orderRepository;

    public OrderPersister(IOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order persist(Order order) {
        return orderRepository.save(order);
    }
}

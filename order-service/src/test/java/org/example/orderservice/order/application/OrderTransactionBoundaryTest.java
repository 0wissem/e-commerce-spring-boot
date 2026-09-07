package org.example.orderservice.order.application;

import org.example.orderservice.AbstractIntegrationTest;
import org.example.orderservice.order.application.dto.OrderItemRequest;
import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.infrastructure.CustomerServiceClient;
import org.example.orderservice.order.infrastructure.ProductServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proves the HTTP-in-transaction bug is actually gone.
 *
 * The old create() was @Transactional and made every cross-service call inside that boundary,
 * pinning a database connection for the whole round trip — connection-pool exhaustion waiting
 * for load. The comment on OrderService claims that is fixed; this test is the evidence.
 *
 * The trick: TransactionSynchronizationManager can be asked, from inside the mocked HTTP
 * client, whether a transaction is currently active on this thread. If any remote call sees
 * one, the boundary is still too wide.
 */
class OrderTransactionBoundaryTest extends AbstractIntegrationTest {

    @Autowired private IOrderService orderService;

    @MockitoBean private ProductServiceClient productServiceClient;
    @MockitoBean private CustomerServiceClient customerServiceClient;

    private static final String CUSTOMER = "cust-boundary";

    private ProductServiceClient.ProductData product(String id) {
        return new ProductServiceClient.ProductData(
                id, "SKU-" + id, "Product " + id, "ACME",
                new BigDecimal("10.00"), "EUR", Set.of());
    }

    @Test
    @DisplayName("NO remote call happens inside a transaction — the pool is never held over I/O")
    void remoteCallsRunOutsideTheTransaction() {
        Queue<String> callsInsideTransaction = new ConcurrentLinkedQueue<>();

        when(customerServiceClient.getById(anyString())).thenAnswer(inv -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                callsInsideTransaction.add("customerServiceClient.getById");
            }
            return new CustomerServiceClient.CustomerData(CUSTOMER, "Alice", "a@e.com");
        });

        when(productServiceClient.getById(anyString())).thenAnswer(inv -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                callsInsideTransaction.add("productServiceClient.getById");
            }
            return product(inv.getArgument(0));
        });

        when(productServiceClient.decrementStock(anyString(), anyInt(), any())).thenAnswer(inv -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                callsInsideTransaction.add("productServiceClient.decrementStock");
            }
            return product(inv.getArgument(0));
        });

        orderService.create(new OrderRequest(CUSTOMER, List.of(
                new OrderItemRequest("p1", 1),
                new OrderItemRequest("p2", 2)
        ), null));

        // THE ASSERTION. Before the refactor this list would contain every remote call.
        assertThat(callsInsideTransaction)
                .as("remote calls made while holding a DB connection")
                .isEmpty();
    }

    @Test
    @DisplayName("product lookups run in PARALLEL, on the dedicated pool — not the caller's thread")
    void productLookupsRunInParallel() {
        Queue<String> threads = new ConcurrentLinkedQueue<>();

        when(customerServiceClient.getById(anyString()))
                .thenReturn(new CustomerServiceClient.CustomerData(CUSTOMER, "Alice", "a@e.com"));
        when(productServiceClient.getById(anyString())).thenAnswer(inv -> {
            threads.add(Thread.currentThread().getName());
            Thread.sleep(50);          // hold the slot so overlap is observable
            return product(inv.getArgument(0));
        });
        when(productServiceClient.decrementStock(anyString(), anyInt(), any()))
                .thenAnswer(inv -> product(inv.getArgument(0)));

        long startedAt = System.currentTimeMillis();
        orderService.create(new OrderRequest(CUSTOMER, List.of(
                new OrderItemRequest("p1", 1),
                new OrderItemRequest("p2", 1),
                new OrderItemRequest("p3", 1)
        ), null));
        long elapsed = System.currentTimeMillis() - startedAt;

        // Three 50ms lookups sequentially would be ~150ms; in parallel it is ~50ms.
        // The bound is generous — this asserts concurrency, not a benchmark.
        assertThat(elapsed)
                .as("three 50ms lookups should overlap, not queue")
                .isLessThan(140);

        // And they run on the dedicated pool, not on the request thread.
        assertThat(threads).allSatisfy(name ->
                assertThat(name).startsWith("order-lookup-"));
    }
}

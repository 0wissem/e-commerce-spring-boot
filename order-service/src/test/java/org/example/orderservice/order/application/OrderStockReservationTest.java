package org.example.orderservice.order.application;

import org.example.orderservice.AbstractIntegrationTest;
import org.example.orderservice.order.application.dto.OrderItemRequest;
import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.domain.IOrderRepository;
import org.example.orderservice.order.domain.InsufficientStockException;
import org.example.orderservice.order.infrastructure.CustomerServiceClient;
import org.example.orderservice.order.infrastructure.ProductServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stock reservation and its compensating action.
 *
 * The HTTP clients are mocked — the point here is order-service's ORCHESTRATION, not
 * product-service's locking (which StockConcurrencyTest covers against real Postgres).
 */
@Transactional   // rolls back after each test, so orders don't leak between them
class OrderStockReservationTest extends AbstractIntegrationTest {

    @Autowired private IOrderService orderService;
    @Autowired private IOrderRepository orderRepository;

    @MockitoBean private ProductServiceClient productServiceClient;
    @MockitoBean private CustomerServiceClient customerServiceClient;

    private static final String CUSTOMER = "cust-1";

    private ProductServiceClient.ProductData product(String id) {
        return new ProductServiceClient.ProductData(
                id, "SKU-" + id, "Product " + id, "ACME",
                new BigDecimal("10.00"), "EUR", Set.of());
    }

    @BeforeEach
    void setUp() {
        when(customerServiceClient.getById(CUSTOMER))
                .thenReturn(new CustomerServiceClient.CustomerData(CUSTOMER, "Alice", "a@e.com"));
        // Product data is now fetched UP FRONT, in parallel with the customer, before any
        // reservation happens — so every test needs it stubbed, including the ones that
        // expect the reservation to fail.
        when(productServiceClient.getById(anyString()))
                .thenAnswer(inv -> product(inv.getArgument(0)));
    }

    @Test
    @DisplayName("stock is reserved for every line before the order is persisted")
    void reservesStockForEachLine() {
        when(productServiceClient.decrementStock(anyString(), anyInt(), any()))
                .thenAnswer(inv -> product(inv.getArgument(0)));

        orderService.create(new OrderRequest(CUSTOMER, List.of(
                new OrderItemRequest("p1", 2),
                new OrderItemRequest("p2", 3)
        ), null));

        verify(productServiceClient).decrementStock("p1", 2, null);
        verify(productServiceClient).decrementStock("p2", 3, null);
        assertThat(orderRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("no stock, no order: a refused reservation aborts the whole create")
    void insufficientStockAbortsTheOrder() {
        when(productServiceClient.decrementStock(eq("p1"), anyInt(), any()))
                .thenThrow(new InsufficientStockException("p1", 5));

        assertThatThrownBy(() -> orderService.create(
                new OrderRequest(CUSTOMER, List.of(new OrderItemRequest("p1", 5)), null)))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("COMPENSATION: line 1 is released when line 2 is refused")
    void releasesEarlierReservationsWhenALaterLineFails() {
        // p1 reserves fine; p2 has no stock.
        when(productServiceClient.decrementStock(eq("p1"), anyInt(), any()))
                .thenReturn(product("p1"));
        when(productServiceClient.decrementStock(eq("p2"), anyInt(), any()))
                .thenThrow(new InsufficientStockException("p2", 1));

        assertThatThrownBy(() -> orderService.create(
                new OrderRequest(CUSTOMER, List.of(
                        new OrderItemRequest("p1", 2),
                        new OrderItemRequest("p2", 1)
                ), null)))
                .isInstanceOf(InsufficientStockException.class);

        // THE assertion: p1's 2 units are handed back. @Transactional cannot do this —
        // the reservation was committed in ANOTHER service's database.
        verify(productServiceClient).incrementStock("p1", 2, null);
        // p2 never succeeded, so there is nothing to release for it.
        verify(productServiceClient, never()).incrementStock(eq("p2"), anyInt(), any());

        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("a failed release is logged, not rethrown — the original cause must survive")
    void releaseFailureDoesNotMaskTheOriginalError() {
        when(productServiceClient.decrementStock(eq("p1"), anyInt(), any()))
                .thenReturn(product("p1"));
        when(productServiceClient.decrementStock(eq("p2"), anyInt(), any()))
                .thenThrow(new InsufficientStockException("p2", 1));
        // The compensation itself fails too.
        when(productServiceClient.incrementStock(anyString(), anyInt(), any()))
                .thenThrow(new RuntimeException("product-service unreachable"));

        // The caller still sees WHY the order failed, not the cleanup failure.
        assertThatThrownBy(() -> orderService.create(
                new OrderRequest(CUSTOMER, List.of(
                        new OrderItemRequest("p1", 2),
                        new OrderItemRequest("p2", 1)
                ), null)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("p2");
    }
}

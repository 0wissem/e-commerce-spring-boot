package org.example.orderservice.order.application;

import org.example.orderservice.AbstractIntegrationTest;
import org.example.orderservice.order.application.dto.OrderItemRequest;
import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.domain.IOrderRepository;
import org.example.orderservice.order.infrastructure.CustomerServiceClient;
import org.example.orderservice.order.infrastructure.ProductServiceClient;
import org.example.orderservice.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Proves @Transactional rollback on OrderService.create. The HTTP collaborators are
 * mocked (@MockitoBean) so we can make a lookup fail mid-flight. The test is NOT
 * @Transactional on purpose — create() manages its own transaction, so we can observe
 * whether it actually committed or rolled back in the real (Testcontainers) DB.
 */
class OrderTransactionalTest extends AbstractIntegrationTest {

    @Autowired
    private IOrderService orderService;
    @Autowired
    private IOrderRepository orderRepository;

    @MockitoBean
    private ProductServiceClient productServiceClient;
    @MockitoBean
    private CustomerServiceClient customerServiceClient;

    @AfterEach
    void cleanup() {
        // committed rows persist (test isn't transactional) → clean up between tests
        orderRepository.findAll().forEach(o -> orderRepository.deleteById(o.getId()));
    }

    @Test
    @DisplayName("rollback: if the 2nd product lookup fails, NO order is persisted")
    void rollsBackOnFailureMidway() {
        when(customerServiceClient.getById("cust-1"))
                .thenReturn(new CustomerServiceClient.CustomerData("cust-1", "Alice", "a@e.com"));
        when(productServiceClient.getById("prod-ok"))
                .thenReturn(new ProductServiceClient.ProductData("prod-ok", "Keyboard", 100.0, Set.of()));
        // 2nd item blows up mid-transaction (a RuntimeException → triggers rollback)
        when(productServiceClient.getById("prod-bad"))
                .thenThrow(new ResourceNotFoundException("Product", "prod-bad"));

        OrderRequest request = new OrderRequest("cust-1", List.of(
                new OrderItemRequest("prod-ok", 2),
                new OrderItemRequest("prod-bad", 1)
        ));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        // @Transactional rolled the whole unit of work back → not even a partial order
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("commit: when all lookups succeed, the order + its items are persisted")
    void commitsOnSuccess() {
        when(customerServiceClient.getById("cust-1"))
                .thenReturn(new CustomerServiceClient.CustomerData("cust-1", "Alice", "a@e.com"));
        when(productServiceClient.getById("prod-ok"))
                .thenReturn(new ProductServiceClient.ProductData("prod-ok", "Keyboard", 100.0, Set.of()));

        OrderRequest request = new OrderRequest("cust-1", List.of(new OrderItemRequest("prod-ok", 2)));
        orderService.create(request);

        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(orderRepository.findAll().get(0).getCustomerName()).isEqualTo("Alice");
    }
}

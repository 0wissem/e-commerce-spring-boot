package org.example.orderservice.order.application;

import org.example.orderservice.order.application.dto.OrderStatusRequest;
import org.example.orderservice.order.domain.IOrderRepository;
import org.example.orderservice.order.domain.Order;
import org.example.orderservice.order.domain.OrderStatus;
import org.example.orderservice.order.infrastructure.CustomerServiceClient;
import org.example.orderservice.order.infrastructure.ProductServiceClient;
import org.example.orderservice.order.application.dto.OrderResponse;
import org.example.orderservice.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for OrderService — collaborators mocked, real mapper. (create() is covered
 * separately by the integration test OrderTransactionalTest; here we cover the rest.)
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private IOrderRepository orderRepository;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private CustomerServiceClient customerServiceClient;
    private final OrderMapper orderMapper = new OrderMapper();

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, productServiceClient, customerServiceClient, orderMapper);
    }

    private Order order(String id) {
        Order o = new Order(id, "cust-1", "Alice", 120.0, OrderStatus.PENDING);
        o.setOrderItems(List.of());
        return o;
    }

    @Test
    @DisplayName("getById: returns the mapped order when found")
    void getById_found() {
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order("o1")));

        OrderResponse response = service.getById("o1");

        assertThat(response.id()).isEqualTo("o1");
        assertThat(response.customerName()).isEqualTo("Alice");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("getById: throws ResourceNotFoundException when missing")
    void getById_missing() {
        when(orderRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    @DisplayName("updateStatus: changes the status and saves")
    void updateStatus_changesAndSaves() {
        Order existing = order("o1");
        when(orderRepository.findById("o1")).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.updateStatus("o1", new OrderStatusRequest(OrderStatus.SHIPPED));

        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(existing.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository).save(existing);
    }

    @Test
    @DisplayName("delete: deletes when the order exists")
    void delete_exists() {
        when(orderRepository.existsById("o1")).thenReturn(true);

        service.delete("o1");

        verify(orderRepository).deleteById("o1");
    }

    @Test
    @DisplayName("delete: throws and never deletes when the order is missing")
    void delete_missing() {
        when(orderRepository.existsById("nope")).thenReturn(false);

        assertThatThrownBy(() -> service.delete("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).deleteById(anyString());
    }
}

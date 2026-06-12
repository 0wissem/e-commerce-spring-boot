package org.example.orderservice.order.application;

import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.application.dto.OrderResponse;
import org.example.orderservice.order.application.dto.OrderStatusRequest;
import org.example.orderservice.order.domain.IOrderRepository;
import org.example.orderservice.order.domain.Order;
import org.example.orderservice.order.domain.OrderItem;
import org.example.orderservice.order.domain.OrderProductSnapshot;
import org.example.orderservice.order.domain.OrderStatus;
import org.example.orderservice.order.infrastructure.CustomerServiceClient;
import org.example.orderservice.order.infrastructure.ProductServiceClient;
import org.example.orderservice.shared.exception.ResourceNotFoundException;
import org.example.orderservice.shared.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OrderService implements IOrderService {

    private final IOrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final OrderMapper orderMapper;

    public OrderService(IOrderRepository orderRepository,
                        ProductServiceClient productServiceClient,
                        CustomerServiceClient customerServiceClient,
                        OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.productServiceClient = productServiceClient;
        this.customerServiceClient = customerServiceClient;
        this.orderMapper = orderMapper;
    }

    @Override
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    public PageResponse<OrderResponse> getAll(int page, int size) {
        return PageResponse.from(
                orderRepository.findAll(PageRequest.of(page, size))
                        .map(orderMapper::toResponse)
        );
    }

    @Override
    public OrderResponse getById(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return orderMapper.toResponse(order);
    }

    @Override
    public List<OrderResponse> getByCustomerId(String customerId) {
        // No customer lookup needed: the customer name is snapshotted on each order.
        return orderRepository.findByCustomerId(customerId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    public OrderResponse create(OrderRequest request) {
        // 1. Resolve the customer name from the monolith (snapshot taken at creation time).
        CustomerServiceClient.CustomerData customer =
                customerServiceClient.getById(request.customerId());

        // 2. For each requested item, fetch product data from product-service and freeze a snapshot.
        List<OrderItem> items = request.items().stream().map(itemRequest -> {
            ProductServiceClient.ProductData product =
                    productServiceClient.getById(itemRequest.productId());

            List<OrderProductSnapshot.CategorySnapshot> categories = product.categories().stream()
                    .map(c -> new OrderProductSnapshot.CategorySnapshot(c.id(), c.name()))
                    .toList();

            OrderProductSnapshot snapshot =
                    new OrderProductSnapshot(product.name(), product.price(), categories);

            double lineTotal = product.price() * itemRequest.quantity();

            return new OrderItem(
                    null,
                    product.id(),
                    product.name(),
                    product.price(),
                    snapshot,
                    itemRequest.quantity(),
                    lineTotal
            );
        }).toList();

        // 3. Total + assemble the order with the customer snapshot.
        double totalPrice = items.stream().mapToDouble(OrderItem::getTotalPrice).sum();

        Order order = new Order(
                null,
                customer.id(),
                customer.name(),
                totalPrice,
                OrderStatus.PENDING
        );

        // 4. Link both sides of the relationship so the cascade persists the items with the order.
        order.setOrderItems(items);
        items.forEach(item -> item.setOrder(order));

        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    public OrderResponse updateStatus(String id, OrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.setStatus(request.status());
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    public void delete(String id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order", id);
        }
        orderRepository.deleteById(id);
    }
}

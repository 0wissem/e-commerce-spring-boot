package org.example.springboot0.order.application;

import org.example.springboot0.customer.domain.Customer;
import org.example.springboot0.customer.domain.ICustomerRepository;
import org.example.springboot0.order.application.dto.OrderRequest;
import org.example.springboot0.order.application.dto.OrderResponse;
import org.example.springboot0.order.application.dto.OrderStatusRequest;
import org.example.springboot0.order.domain.IOrderRepository;
import org.example.springboot0.order.domain.Order;
import org.example.springboot0.order.domain.OrderItem;
import org.example.springboot0.order.domain.OrderProductSnapshot;
import org.example.springboot0.order.domain.OrderStatus;
import org.example.springboot0.product.infrastructure.ProductServiceClient;
import org.example.springboot0.shared.event.CategoryDto;
import org.example.springboot0.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OrderService implements IOrderService {

    private final IOrderRepository orderRepository;
    private final ICustomerRepository customerRepository;
    private final ProductServiceClient productServiceClient;
    private final OrderMapper orderMapper;

    public OrderService(IOrderRepository orderRepository,
                        ICustomerRepository customerRepository,
                        ProductServiceClient productServiceClient,
                        OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productServiceClient = productServiceClient;
        this.orderMapper = orderMapper;
    }

    @Override
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(order -> orderMapper.toResponse(order, order.getCustomer().getName()))
                .toList();
    }

    public OrderResponse getById(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return orderMapper.toResponse(order, order.getCustomer().getName());
    }

    @Override
    public List<OrderResponse> getByCustomerId(String customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        return orderRepository.findByCustomerId(customerId).stream()
                .map(order -> orderMapper.toResponse(order, customer.getName()))
                .toList();
    }

    @Override
    public OrderResponse create(OrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        List<OrderItem> items = request.items().stream().map(itemRequest -> {
            ProductServiceClient.ProductData product = productServiceClient.getById(itemRequest.productId());
            List<CategoryDto> categories = product.categories().stream()
                    .map(c -> new CategoryDto(c.id(), c.name()))
                    .toList();
            OrderProductSnapshot snapshot = new OrderProductSnapshot(product.name(), product.price(), categories);
            return new OrderItem(null, product.id(), product.name(), itemRequest.quantity(), product.price(), snapshot);
        }).toList();

        double totalPrice = items.stream().mapToDouble(OrderItem::getSubtotal).sum();

        Order order = new Order(null, customer, OrderStatus.PENDING, items, totalPrice);
        items.forEach(item -> item.setOrder(order));
        return orderMapper.toResponse(orderRepository.save(order), customer.getName());
    }

    @Override
    public OrderResponse updateStatus(String id, OrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.setStatus(request.status());
        return orderMapper.toResponse(orderRepository.save(order), order.getCustomer().getName());
    }

    @Override
    public void delete(String id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order", id);
        }
        orderRepository.deleteById(id);
    }
}
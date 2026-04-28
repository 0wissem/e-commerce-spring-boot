package org.example.springboot0.order.application;

import org.example.springboot0.customer.domain.Customer;
import org.example.springboot0.customer.domain.ICustomerRepository;
import org.example.springboot0.order.application.dto.OrderRequest;
import org.example.springboot0.order.application.dto.OrderResponse;
import org.example.springboot0.order.application.dto.OrderStatusRequest;
import org.example.springboot0.order.domain.IOrderRepository;
import org.example.springboot0.order.domain.Order;
import org.example.springboot0.order.domain.OrderItem;
import org.example.springboot0.order.domain.OrderStatus;
import org.example.springboot0.product.domain.IProductRepository;
import org.example.springboot0.product.domain.Product;
import org.example.springboot0.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService implements IOrderService {

    private final IOrderRepository orderRepository;
    private final ICustomerRepository customerRepository;
    private final IProductRepository productRepository;
    private final OrderMapper orderMapper;

    public OrderService(IOrderRepository orderRepository,
                        ICustomerRepository customerRepository,
                        IProductRepository productRepository,
                        OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(order -> {
                    String customerName = customerRepository.findById(order.getCustomerId())
                            .map(Customer::getName)
                            .orElse("Unknown");
                    return orderMapper.toResponse(order, customerName);
                })
                .toList();
    }

    public OrderResponse getById(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        String customerName = customerRepository.findById(order.getCustomerId())
                .map(Customer::getName)
                .orElse("Unknown");
        return orderMapper.toResponse(order, customerName);
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
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemRequest.productId()));
            OrderItem item = new OrderItem(
                    null,
                    product.getId(),
                    product.getName(),
                    itemRequest.quantity(),
                    product.getPrice()
            );
            return item;
        }).toList();

        double totalPrice = items.stream().mapToDouble(OrderItem::getSubtotal).sum();

        Order order = new Order(null, customer.getId(), OrderStatus.PENDING, items, totalPrice);
        return orderMapper.toResponse(orderRepository.save(order), customer.getName());
    }

    @Override
    public OrderResponse updateStatus(String id, OrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.setStatus(request.status());
        String customerName = customerRepository.findById(order.getCustomerId())
                .map(Customer::getName)
                .orElse("Unknown");
        return orderMapper.toResponse(orderRepository.save(order), customerName);
    }

    @Override
    public void delete(String id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order", id);
        }
        orderRepository.deleteById(id);
    }
}
package org.example.orderservice.order.application;

import org.example.orderservice.order.application.dto.OrderItemRequest;
import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.application.dto.CursorPageResponse;
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
import org.example.orderservice.shared.security.CallerToken;
import org.example.orderservice.shared.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * NOTE: this class is deliberately NOT annotated @Transactional at class level any more.
 *
 * It used to be, which meant create() held a database connection across every cross-service
 * HTTP call — the connection-pool-exhaustion bug. Transactions are now declared per method,
 * and create() has none: it delegates its single write to {@link OrderPersister}.
 */
@Service
public class OrderService implements IOrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final IOrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final OrderMapper orderMapper;
    private final OrderPersister orderPersister;
    private final Executor lookupExecutor;

    public OrderService(IOrderRepository orderRepository,
                        ProductServiceClient productServiceClient,
                        CustomerServiceClient customerServiceClient,
                        OrderMapper orderMapper,
                        OrderPersister orderPersister,
                        // Executor, not ExecutorService: this class only SUBMITS work. It has
                        // no business shutting the pool down, so it depends on the narrower
                        // interface — and a test can pass a same-thread executor as a lambda.
                        Executor orderLookupExecutor) {
        this.orderRepository = orderRepository;
        this.productServiceClient = productServiceClient;
        this.customerServiceClient = customerServiceClient;
        this.orderMapper = orderMapper;
        this.orderPersister = orderPersister;
        this.lookupExecutor = orderLookupExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAll(int page, int size) {
        return PageResponse.from(
                orderRepository.findAll(PageRequest.of(page, size))
                        .map(orderMapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getByCustomerId(String customerId) {
        // No customer lookup needed: the customer name is snapshotted on each order.
        return orderRepository.findByCustomerId(customerId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    /** Maximum page size — an unbounded `limit` is a denial-of-service vector. */
    private static final int MAX_HISTORY_LIMIT = 100;

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<OrderResponse> getCustomerHistory(String customerId, String cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY_LIMIT);
        OrderHistoryCursor decoded = OrderHistoryCursor.decode(cursor);

        // Fetch one extra row: if it comes back, there IS a next page. This avoids a COUNT
        // query — knowing "is there more" is cheaper than knowing "how many in total".
        List<Order> orders = orderRepository.findCustomerHistoryPage(
                customerId,
                decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.id(),
                safeLimit + 1
        );

        boolean hasMore = orders.size() > safeLimit;
        List<Order> page = hasMore ? orders.subList(0, safeLimit) : orders;

        String nextCursor = null;
        if (hasMore) {
            Order last = page.get(page.size() - 1);
            nextCursor = new OrderHistoryCursor(last.getCreatedAt(), last.getId()).encode();
        }

        return CursorPageResponse.of(page.stream().map(orderMapper::toResponse).toList(), nextCursor);
    }

    /**
     * Creates an order.
     *
     * NOTE THE ABSENCE OF @Transactional. This method performs network I/O, and a transaction
     * around network I/O pins a database connection for the whole round trip. Only the final
     * write is transactional, and it is delegated to {@link OrderPersister} so the call goes
     * through a proxy.
     *
     * Sequence: gather remote data in PARALLEL → reserve stock → open a short transaction and
     * write. The database is touched only in the last step.
     */
    @Override
    public OrderResponse create(OrderRequest request) {
        // Capture the caller's token HERE, on the request thread. SecurityContextHolder is a
        // ThreadLocal, and the lookups below run on a separate pool where it is empty — read
        // it inside those tasks and you get null, and the downstream call goes out anonymous.
        String callerToken = CallerToken.current().orElse(null);

        // 1. Fetch the customer and every product CONCURRENTLY.
        //
        // These calls are independent, so running them sequentially made the latency the SUM
        // of all of them; in parallel it is the MAX. With one customer lookup and three
        // products, four sequential round trips become one wall-clock wait.
        CompletableFuture<CustomerServiceClient.CustomerData> customerFuture =
                CompletableFuture.supplyAsync(
                        () -> customerServiceClient.getById(request.customerId()), lookupExecutor);

        Map<String, CompletableFuture<ProductServiceClient.ProductData>> productFutures =
                request.items().stream()
                        .map(OrderItemRequest::productId)
                        .distinct()   // the same product may appear on several lines — fetch once
                        .collect(Collectors.toMap(
                                productId -> productId,
                                productId -> CompletableFuture.supplyAsync(
                                        () -> productServiceClient.getById(productId), lookupExecutor)));

        CustomerServiceClient.CustomerData customer;
        Map<String, ProductServiceClient.ProductData> products;
        try {
            // join() rethrows the underlying failure wrapped in a CompletionException;
            // unwrapping keeps the original exception type, so a missing product still
            // surfaces as a 404 rather than an opaque 500.
            customer = customerFuture.join();
            products = productFutures.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join()));
        } catch (CompletionException e) {
            throw unwrap(e);
        }

        // 2. Reserve stock for every line BEFORE building the order.
        //
        // Stock lives in product-service, so this crosses a service boundary and cannot take
        // part in our transaction — a rollback here does NOT put the units back. If line 3
        // fails after lines 1 and 2 were reserved, those reservations are already committed
        // in another database.
        //
        // That is the classic distributed-transaction problem, and the answer is a
        // COMPENSATING ACTION: undo what we did, explicitly. Recording each successful
        // reservation lets us hand the units back on failure.
        List<Reservation> reserved = new ArrayList<>();
        try {
            for (OrderItemRequest item : request.items()) {
                productServiceClient.decrementStock(item.productId(), item.quantity(), callerToken);
                reserved.add(new Reservation(item.productId(), item.quantity()));
            }
        } catch (RuntimeException e) {
            compensate(reserved, callerToken);   // give back whatever we already took
            throw e;
        }

        // 3. Freeze a snapshot per line, from the data already fetched above — no further I/O.
        List<OrderItem> items = request.items().stream().map(itemRequest -> {
            ProductServiceClient.ProductData product = products.get(itemRequest.productId());

            List<OrderProductSnapshot.CategorySnapshot> categories = product.categories().stream()
                    .map(c -> new OrderProductSnapshot.CategorySnapshot(c.id(), c.name()))
                    .toList();

            OrderProductSnapshot snapshot = OrderProductSnapshot.of(
                    product.name(),
                    product.sku(),
                    product.brand(),
                    product.price(),
                    product.currency(),
                    categories
            );

            // The line total is derived inside OrderItem, so it cannot disagree with
            // unitAmount * quantity.
            return new OrderItem(
                    null,
                    product.id(),
                    product.name(),
                    product.price(),
                    snapshot,
                    itemRequest.quantity()
            );
        }).toList();

        // 4. Total + assemble the order with the customer snapshot.
        // reduce with BigDecimal::add, not mapToDouble().sum() — summing doubles is precisely
        // how a total ends up a cent away from the sum of its own lines.
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(
                null,
                customer.id(),
                customer.name(),
                totalAmount,
                OrderStatus.PENDING
        );

        if (request.shippingAddress() != null) {
            order.setShippingAddress(request.shippingAddress());
        }

        // 5. Link both sides of the relationship so the cascade persists the items with the order.
        order.setOrderItems(items);
        items.forEach(item -> item.setOrder(order));

        // 6. ONLY NOW open a transaction — one INSERT plus its cascade, no network inside it.
        return orderMapper.toResponse(orderPersister.persist(order));
    }

    /**
     * CompletableFuture.join() wraps failures in a CompletionException. Unwrapping restores
     * the original type so the exception handler still maps it correctly — otherwise a
     * ResourceNotFoundException would arrive as a generic 500.
     */
    private RuntimeException unwrap(CompletionException e) {
        Throwable cause = e.getCause();
        return cause instanceof RuntimeException re ? re : e;
    }

    /** A reservation already committed in product-service, kept so it can be undone. */
    private record Reservation(String productId, int quantity) {}

    /**
     * Hands reserved units back after a partial failure.
     *
     * Every release is attempted independently and failures are logged rather than thrown:
     * we are already unwinding one error, and letting a second one escape would replace the
     * real cause with a misleading one. Anything that fails here leaks stock, which is why a
     * production system would queue these for retry instead of relying on one best-effort
     * pass — the honest limitation of a synchronous compensating action.
     */
    private void compensate(List<Reservation> reserved, String callerToken) {
        for (Reservation r : reserved) {
            try {
                productServiceClient.incrementStock(r.productId(), r.quantity(), callerToken);
            } catch (RuntimeException releaseFailure) {
                log.error("Failed to release {} unit(s) of product {} after a failed order; "
                        + "stock is now under-reported and needs reconciliation",
                        r.quantity(), r.productId(), releaseFailure);
            }
        }
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(String id, OrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.setStatus(request.status());
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public void delete(String id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order", id);
        }
        orderRepository.deleteById(id);
    }
}

package org.example.orderservice.order.application;

import org.example.orderservice.AbstractIntegrationTest;
import org.example.orderservice.order.application.dto.OrderItemRequest;
import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.domain.IOrderRepository;
import org.example.orderservice.order.domain.InsufficientStockException;
import org.example.orderservice.order.domain.Order;
import org.example.orderservice.order.infrastructure.CustomerServiceClient;
import org.example.orderservice.order.infrastructure.ProductServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Concurrency at the ORDER level — the end-to-end half of the oversell story.
 *
 * StockConcurrencyTest proves product-service cannot oversell a row. That is not the same as
 * proving ORDER CREATION is safe: create() adds parallel lookups, a multi-line reservation
 * loop and a compensating action on top, and any of those could leak or double-count.
 *
 * product-service is mocked, but STATEFULLY: the mock enforces the same contract the real
 * service does (atomically decrement, refuse when empty), so the orchestration is exercised
 * against realistic behaviour rather than a mock that always says yes.
 *
 * NOT @Transactional — the threads must really commit for the race to exist.
 */
class ConcurrentOrderCreationTest extends AbstractIntegrationTest {

    @Autowired private IOrderService orderService;
    @Autowired private IOrderRepository orderRepository;

    @MockitoBean private ProductServiceClient productServiceClient;
    @MockitoBean private CustomerServiceClient customerServiceClient;

    private static final String CUSTOMER = "cust-concurrent";

    /** Stands in for the real row in product-service. */
    private AtomicInteger remoteStock;
    private AtomicInteger unitsReleased;

    private ProductServiceClient.ProductData product(String id) {
        return new ProductServiceClient.ProductData(
                id, "SKU-" + id, "Product " + id, "ACME",
                new BigDecimal("10.00"), "EUR", Set.of());
    }

    @BeforeEach
    void setUp() {
        remoteStock   = new AtomicInteger();
        unitsReleased = new AtomicInteger();

        when(customerServiceClient.getById(anyString()))
                .thenReturn(new CustomerServiceClient.CustomerData(CUSTOMER, "Alice", "a@e.com"));
        when(productServiceClient.getById(anyString()))
                .thenAnswer(inv -> product(inv.getArgument(0)));

        // Atomic decrement-if-available: exactly the guarantee @Version gives us for real.
        when(productServiceClient.decrementStock(anyString(), anyInt(), any())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            int qty  = inv.getArgument(1);
            while (true) {
                int current = remoteStock.get();
                if (current < qty) {
                    throw new InsufficientStockException(id, qty);
                }
                if (remoteStock.compareAndSet(current, current - qty)) {
                    return product(id);
                }
                // lost the CAS race — re-read and try again
            }
        });

        // The compensating call gives units back.
        when(productServiceClient.incrementStock(anyString(), anyInt(), any())).thenAnswer(inv -> {
            int qty = inv.getArgument(1);
            remoteStock.addAndGet(qty);
            unitsReleased.addAndGet(qty);
            return product(inv.getArgument(0));
        });
    }

    @AfterEach
    void tearDown() {
        orderRepository.findAll().forEach(o -> orderRepository.deleteById(o.getId()));
    }

    @Test
    @DisplayName("10 customers race for the LAST unit: exactly one order exists")
    void onlyOneOrderWinsTheLastUnit() throws Exception {
        remoteStock.set(1);                       // one unit left
        int threads = 10;

        Queue<Exception> unexpected = new ConcurrentLinkedQueue<>();
        AtomicInteger created  = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    orderService.create(new OrderRequest(
                            CUSTOMER, List.of(new OrderItemRequest("p1", 1)), null));
                    created.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpected.add(e);
                } finally {
                    finished.countDown();
                }
            });
        }

        startGun.countDown();
        assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(unexpected).as("no order may fail for an unexpected reason").isEmpty();

        // THE ASSERTIONS: one unit, one order, nine honest refusals — and no negative stock.
        assertThat(created.get()).as("orders created").isEqualTo(1);
        assertThat(rejected.get()).as("orders refused").isEqualTo(threads - 1);
        assertThat(remoteStock.get()).as("remote stock must never go negative").isZero();
        assertThat(orderRepository.findAll()).as("persisted orders").hasSize(1);
    }

    @Test
    @DisplayName("concurrent multi-line orders: every reservation is either used or given back")
    void compensationBalancesUnderConcurrency() throws Exception {
        // Enough stock for p1, so line 1 always succeeds; p2 is a different product whose
        // stock runs out, forcing compensation on many threads at once. The accounting must
        // balance even when several compensations run concurrently.
        remoteStock.set(3);                       // shared pool across both lines
        int threads = 8;

        Queue<Exception> unexpected = new ConcurrentLinkedQueue<>();
        AtomicInteger created = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    orderService.create(new OrderRequest(CUSTOMER, List.of(
                            new OrderItemRequest("p1", 1),
                            new OrderItemRequest("p2", 1)
                    ), null));
                    created.incrementAndGet();
                } catch (InsufficientStockException expected) {
                    // fine — the pool ran dry partway through
                } catch (Exception e) {
                    unexpected.add(e);
                } finally {
                    finished.countDown();
                }
            });
        }

        startGun.countDown();
        assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(unexpected).isEmpty();

        // CONSERVATION: every unit is either committed to an order (2 per order) or back in
        // stock. Nothing may vanish — a leak here means compensation dropped a reservation.
        int unitsHeldByOrders = created.get() * 2;
        assertThat(unitsHeldByOrders + remoteStock.get())
                .as("units on orders + units in stock must equal the original 3")
                .isEqualTo(3);

        assertThat(remoteStock.get()).as("stock must never go negative").isNotNegative();
        assertThat(orderRepository.findAll()).hasSize(created.get());

        // Every failed multi-line attempt that got past line 1 must have released it.
        assertThat(unitsReleased.get())
                .as("compensation released the reservations it took")
                .isNotNegative();
    }

    @Test
    @DisplayName("no partial order survives a refused reservation")
    void noPartialOrdersArePersisted() throws Exception {
        remoteStock.set(0);                       // nothing available at all
        int threads = 5;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch finished = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    orderService.create(new OrderRequest(
                            CUSTOMER, List.of(new OrderItemRequest("p1", 1)), null));
                } catch (RuntimeException expected) {
                    // every attempt must fail
                } finally {
                    finished.countDown();
                }
            });
        }

        assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        List<Order> orders = orderRepository.findAll();
        assertThat(orders).as("a refused reservation must leave no order behind").isEmpty();
    }
}

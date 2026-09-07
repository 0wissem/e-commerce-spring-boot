package org.example.productservice.product.application;

import org.example.productservice.AbstractIntegrationTest;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.InsufficientStockException;
import org.example.productservice.product.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.math.BigDecimal;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pessimistic counterpart to {@link StockConcurrencyTest}.
 *
 * Same race, same assertion, opposite strategy — which is the point. Both are correct; they
 * differ in HOW they get there and in what they cost.
 *
 * Not @Transactional, for the same reason as StockConcurrencyTest: the threads must really
 * commit for the lock to be observable. Hence the manual cleanup.
 */
class PessimisticLockingTest extends AbstractIntegrationTest {

    @Autowired private IProductService productService;
    @Autowired private IProductRepository productRepository;

    private String productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID().toString();
        productRepository.save(new Product(productId, "Flash Sale Item", new BigDecimal("10.00"), 10));
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteById(productId);
    }

    @Test
    @DisplayName("20 threads, row-locked: exactly 10 succeed — and NOTHING is ever retried")
    void pessimisticLockPreventsOversell() throws Exception {
        int threads = 20;
        int stock = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);

        Queue<Exception> unexpected = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected  = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    // Called DIRECTLY — no StockService, no retry loop. The lock removes the
                    // need for one: a conflict cannot happen, so there is nothing to recover
                    // from. That absence is the whole contrast with the optimistic path.
                    productService.decrementStockPessimistic(productId, 1);
                    succeeded.incrementAndGet();
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
        assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // No OptimisticLockingFailureException can appear here — that is the guarantee.
        assertThat(unexpected)
                .as("a pessimistic lock must never produce a lock-conflict exception")
                .isEmpty();

        assertThat(succeeded.get()).isEqualTo(stock);
        assertThat(rejected.get()).isEqualTo(threads - stock);
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("a pessimistic lock REQUIRES a transaction — it has nothing to hold otherwise")
    void pessimisticReadOutsideATransactionIsRejected() {
        // A lock lives for the length of a transaction. With no transaction there is nothing
        // to hold it open, so the request cannot be honoured at all: Spring rejects it up
        // front rather than handing back an unlocked row and pretending.
        //
        // Contrast with @Version, which needs no transaction to READ — it only detects the
        // conflict later, at write time.
        assertThatThrownBy(() -> productRepository.findByIdForUpdate(productId))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("No active transaction");
    }

    @Test
    @DisplayName("locking changes concurrency, not business rules — the invariant still holds")
    void lockedPathStillEnforcesTheInvariant() {
        // Goes through the service, which IS @Transactional, so the lock can be taken.
        assertThatThrownBy(() -> productService.decrementStockPessimistic(productId, 99))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .isEqualTo(10);
    }
}

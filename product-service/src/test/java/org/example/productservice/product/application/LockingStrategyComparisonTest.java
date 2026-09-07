package org.example.productservice.product.application;

import org.example.productservice.AbstractIntegrationTest;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.InsufficientStockException;
import org.example.productservice.product.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optimistic vs pessimistic, measured on the same workload.
 *
 * Not a benchmark in the JMH sense — one JVM, one container, no warmup — so the numbers are
 * indicative, not publishable. What it does establish is the SHAPE of the trade-off, and it
 * prints both timings so the difference is visible rather than asserted from theory.
 *
 * Expectation under HIGH contention (every thread fighting for one row):
 *   pessimistic wins — callers queue once, nobody repeats work.
 *   optimistic loses — losers redo their transaction and sleep on backoff.
 *
 * Under LOW contention the ranking reverses, because optimistic takes no lock at all.
 */
class LockingStrategyComparisonTest extends AbstractIntegrationTest {

    @Autowired private StockService stockService;
    @Autowired private IProductService productService;
    @Autowired private IProductRepository productRepository;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(productRepository::deleteById);
        created.clear();
    }

    private String newProductWithStock(int stock) {
        String id = UUID.randomUUID().toString();
        productRepository.save(new Product(id, "Benchmark Item", new BigDecimal("10.00"), stock));
        created.add(id);
        return id;
    }

    @Test
    @DisplayName("both strategies are CORRECT under contention; the pessimistic one is faster here")
    void compareUnderHighContention() throws Exception {
        int threads = 30;
        int stock = 30;

        String optimisticId  = newProductWithStock(stock);
        String pessimisticId = newProductWithStock(stock);

        long optimisticMs = timeConcurrentDecrements(
                optimisticId, threads, (id, qty) -> stockService.decrementStock(id, qty));

        long pessimisticMs = timeConcurrentDecrements(
                pessimisticId, threads, (id, qty) -> productService.decrementStockPessimistic(id, qty));

        System.out.printf("%n  contention: %d threads on 1 row%n", threads);
        System.out.printf("  optimistic (@Version + retry/backoff) : %4d ms%n", optimisticMs);
        System.out.printf("  pessimistic (SELECT ... FOR UPDATE)   : %4d ms%n%n", pessimisticMs);

        // CORRECTNESS is the assertion — both must sell exactly the stock, no more.
        assertThat(productRepository.findById(optimisticId).orElseThrow().getStockQuantity())
                .as("optimistic strategy must not oversell").isZero();
        assertThat(productRepository.findById(pessimisticId).orElseThrow().getStockQuantity())
                .as("pessimistic strategy must not oversell").isZero();

        // Timing is PRINTED, not asserted: wall-clock on a shared CI box is not a contract.
        // Turning an observation into an assertion is how you get a flaky suite.
    }

    /** Runs {@code threads} concurrent single-unit decrements and returns the wall-clock time. */
    private long timeConcurrentDecrements(String productId, int threads,
                                          BiConsumer<String, Integer> decrement) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    decrement.accept(productId, 1);
                } catch (InsufficientStockException expected) {
                    // fine — more threads than stock is a valid outcome
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startGun.countDown();
        assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        pool.shutdown();
        assertThat(failures.get()).as("no strategy may fail outright").isZero();
        return elapsedMs;
    }
}

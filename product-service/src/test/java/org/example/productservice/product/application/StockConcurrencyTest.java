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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the oversell race is actually closed — with real threads, real transactions and a
 * real Postgres.
 *
 * NOTE: this class is deliberately NOT @Transactional. A test-level transaction would make
 * every thread share one connection and roll everything back at the end, so no thread could
 * ever observe another's commit — the exact interleaving under test would be impossible.
 * That is why rows are cleaned up by hand instead.
 */
class StockConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private StockService stockService;
    @Autowired private IProductService productService;
    @Autowired private IProductRepository productRepository;

    private String productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID().toString();
        Product product = new Product(productId, "Limited Item", new BigDecimal("10.00"), 10);
        productRepository.save(product);
    }

    /**
     * Manual cleanup, because this class cannot use @Transactional rollback.
     *
     * Everything here really commits, and the Testcontainers Postgres is shared by the whole
     * suite — so a row left behind is visible to every other test. Omitting this broke two
     * unrelated tests that count rows: they saw four extra products and failed. A test that
     * commits owns its own cleanup.
     */
    @AfterEach
    void tearDown() {
        productRepository.deleteById(productId);
    }

    @Test
    @DisplayName("20 threads racing for 10 units: exactly 10 succeed and stock lands on 0")
    void concurrentDecrements_doNotOversell() throws Exception {
        int threads = 20;
        int stock = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);   // release all threads at once
        CountDownLatch finished = new CountDownLatch(threads);

        Queue<Exception> unexpected = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected  = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();                       // maximise the collision
                    stockService.decrementStock(productId, 1);
                    succeeded.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejected.incrementAndGet();             // the correct business answer
                } catch (Exception e) {
                    // Recorded, never swallowed. Swallowing this is what hid the first failure:
                    // the counts were simply short and the cause was invisible.
                    unexpected.add(e);
                } finally {
                    finished.countDown();
                }
            });
        }

        startGun.countDown();
        assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Surface any unexpected failure with its cause, instead of a bare count mismatch.
        assertThat(unexpected)
                .as("threads that failed for a reason other than insufficient stock")
                .isEmpty();

        // THE assertion: without @Version, more than 10 would succeed — lost updates, and
        // stock would end up negative or wrong. Exactly 10 is the whole point.
        assertThat(succeeded.get()).isEqualTo(stock);
        assertThat(rejected.get()).isEqualTo(threads - stock);

        Product after = productRepository.findById(productId).orElseThrow();
        assertThat(after.getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("@Version increments on every write — the counter the lock is built on")
    void versionIncrementsOnWrite() {
        Long versionBefore = productRepository.findById(productId).orElseThrow().getVersion();

        stockService.decrementStock(productId, 1);

        Product reread = productRepository.findById(productId).orElseThrow();
        assertThat(reread.getVersion()).isGreaterThan(versionBefore);
        assertThat(reread.getStockQuantity()).isEqualTo(9);
    }

    @Test
    @DisplayName("decrementing more than available is refused, and stock is untouched")
    void cannotDecrementBelowZero() {
        assertThatThrownBy(() -> stockService.decrementStock(productId, 11))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 11")
                .hasMessageContaining("available 10");

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .isEqualTo(10);
    }

    @Test
    @DisplayName("the entity refuses a non-positive quantity")
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> stockService.decrementStock(productId, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

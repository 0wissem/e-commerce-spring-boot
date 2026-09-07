package org.example.productservice.product.infrastructure;

import org.example.productservice.AbstractIntegrationTest;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deadlocks, and the discipline that makes them impossible.
 *
 * A deadlock needs a CYCLE: A holds row 1 and wants row 2, B holds row 2 and wants row 1.
 * Neither can proceed. Postgres detects the cycle and kills one victim with SQLState 40P01 —
 * it does not hang forever.
 *
 * The cure is not cleverness, it is CONSISTENT LOCK ORDERING: if every transaction takes rows
 * in the same order (here, sorted by id), a cycle cannot form — someone always gets both locks
 * first and the other simply waits.
 *
 * This matters for real orders: a basket containing products X and Y locks two rows, and two
 * customers buying the same pair in opposite order is exactly the cycle above.
 */
class DeadlockOrderingTest extends AbstractIntegrationTest {

    @Autowired private IProductRepository productRepository;
    @Autowired private DataSource dataSource;

    private String idA;
    private String idB;

    @BeforeEach
    void setUp() {
        idA = "aaaa" + UUID.randomUUID();     // deterministic ordering: idA < idB
        idB = "bbbb" + UUID.randomUUID();
        productRepository.save(new Product(idA, "Product A", new BigDecimal("10.00"), 100));
        productRepository.save(new Product(idB, "Product B", new BigDecimal("10.00"), 100));
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteById(idA);
        productRepository.deleteById(idB);
    }

    @Test
    @DisplayName("OPPOSITE lock order deadlocks — Postgres kills one victim (SQLState 40P01)")
    void oppositeLockOrderDeadlocks() throws Exception {
        Queue<String> sqlStates = new ConcurrentLinkedQueue<>();
        CountDownLatch bothHoldTheirFirstLock = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);

        // Thread 1 locks A then B. Thread 2 locks B then A. That is the cycle.
        Thread t1 = new Thread(() -> lockTwoRows(idA, idB, bothHoldTheirFirstLock, done, sqlStates));
        Thread t2 = new Thread(() -> lockTwoRows(idB, idA, bothHoldTheirFirstLock, done, sqlStates));

        t1.start();
        t2.start();
        assertThat(done.await(30, TimeUnit.SECONDS))
                .as("Postgres must break the deadlock rather than hang")
                .isTrue();

        // 40P01 = deadlock_detected. Exactly one transaction is chosen as the victim.
        assertThat(sqlStates)
                .as("expected exactly one deadlock victim")
                .containsExactly("40P01");
    }

    @Test
    @DisplayName("CONSISTENT lock order cannot deadlock — a cycle is impossible by construction")
    void consistentLockOrderNeverDeadlocks() throws Exception {
        Queue<String> sqlStates = new ConcurrentLinkedQueue<>();
        CountDownLatch bothHoldTheirFirstLock = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);

        // BOTH threads take the rows in the same (sorted) order. The second simply waits.
        List<String> sorted = List.of(idA, idB).stream().sorted().toList();
        Thread t1 = new Thread(() ->
                lockTwoRows(sorted.get(0), sorted.get(1), bothHoldTheirFirstLock, done, sqlStates));
        Thread t2 = new Thread(() ->
                lockTwoRows(sorted.get(0), sorted.get(1), bothHoldTheirFirstLock, done, sqlStates));

        t1.start();
        t2.start();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

        // No victim, no error: one transaction queued behind the other and both finished.
        assertThat(sqlStates)
                .as("sorted lock acquisition must never deadlock")
                .isEmpty();
    }

    /**
     * Locks {@code first}, waits until the other thread also holds its first lock (guaranteeing
     * the interleaving), then reaches for {@code second}.
     */
    private void lockTwoRows(String first, String second,
                             CountDownLatch bothHoldTheirFirstLock,
                             CountDownLatch done,
                             Queue<String> sqlStates) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                selectForUpdate(c, first);

                // Neither thread may reach for its SECOND row until both hold their first —
                // otherwise one finishes before the other starts and no cycle ever forms.
                bothHoldTheirFirstLock.countDown();
                bothHoldTheirFirstLock.await(10, TimeUnit.SECONDS);

                selectForUpdate(c, second);
                c.commit();
            } catch (Exception e) {
                if (e instanceof java.sql.SQLException sqlEx) {
                    sqlStates.add(sqlEx.getSQLState());
                }
                c.rollback();
            }
        } catch (Exception connectionFailure) {
            sqlStates.add("connection-error");
        } finally {
            done.countDown();
        }
    }

    private void selectForUpdate(Connection c, String id) throws Exception {
        try (var st = c.prepareStatement("SELECT id FROM products WHERE id = ? FOR UPDATE")) {
            st.setString(1, id);
            try (var rs = st.executeQuery()) {
                rs.next();
            }
        }
    }
}

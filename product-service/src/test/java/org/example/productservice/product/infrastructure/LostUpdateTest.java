package org.example.productservice.product.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.productservice.AbstractIntegrationTest;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two halves of the lost-update story:
 *
 *  1. WITHOUT a version check, a concurrent read-modify-write silently loses one update —
 *     which is the oversell bug, reproduced at the SQL level.
 *  2. WITH @Version, the same interleaving raises OptimisticLockingFailureException instead.
 *
 * StockConcurrencyTest proves the FIXED behaviour under load. This proves the BUG exists and
 * that the version check is what stops it — otherwise "we added @Version" is an assertion
 * about a mechanism nobody has seen fail.
 */
class LostUpdateTest extends AbstractIntegrationTest {

    @Autowired private IProductRepository productRepository;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private DataSource dataSource;

    @PersistenceContext private EntityManager em;

    private String productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID().toString();
        productRepository.save(new Product(productId, "Contended Item", new BigDecimal("10.00"), 10));
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteById(productId);
    }

    @Test
    @DisplayName("THE BUG: an unguarded read-modify-write loses an update — stock 10 → 9, not 8")
    void unguardedConcurrentUpdatesLoseOne() throws Exception {
        // Raw SQL, deliberately bypassing @Version, to reproduce what the code did BEFORE it
        // existed: two sessions read 10, each computes 10 - 1, each writes 9. Two units were
        // sold; one decrement vanished. That is a lost update, and at scale it is the oversell.
        try (Connection a = dataSource.getConnection();
             Connection b = dataSource.getConnection()) {

            a.setAutoCommit(false);
            b.setAutoCommit(false);

            int readByA = readStock(a);
            int readByB = readStock(b);
            assertThat(readByA).isEqualTo(10);
            assertThat(readByB).isEqualTo(10);   // both saw the SAME starting value

            writeStock(a, readByA - 1);
            a.commit();

            writeStock(b, readByB - 1);          // overwrites A's result with a stale value
            b.commit();
        }

        em.clear();
        int finalStock = productRepository.findById(productId).orElseThrow().getStockQuantity();

        // Two units sold, one unit deducted. THIS is the bug @Version exists to catch.
        assertThat(finalStock)
                .as("two decrements applied to a stale read leave stock wrong")
                .isEqualTo(9)
                .isNotEqualTo(8);
    }

    @Test
    @DisplayName("THE FIX: the same interleaving through JPA raises OptimisticLockingFailureException")
    void versionedConcurrentUpdatesAreRejected() {
        // Load the row, then let ANOTHER transaction commit a change to it.
        TransactionStatus tx = txManager.getTransaction(new DefaultTransactionDefinition());
        Product stale = productRepository.findById(productId).orElseThrow();
        Long staleVersion = stale.getVersion();

        // A competing writer commits first and bumps the version.
        bumpStockInSeparateTransaction();

        // Now write using the stale instance. Hibernate's UPDATE carries
        //   WHERE id = ? AND version = <staleVersion>
        // which matches zero rows, because the version has moved on.
        stale.decrementStock(1);

        // NOTE THE EXCEPTION TYPE — this caught me out and is worth knowing:
        //
        // Flushing the EntityManager directly throws JPA's jakarta.persistence.
        // OptimisticLockException. Spring's OptimisticLockingFailureException is the
        // TRANSLATED form, produced when the failure crosses a Spring boundary (a @Repository
        // proxy or transaction commit). Same event, two types, depending on where you stand.
        //
        // StockService catches the Spring type because the real path flushes at commit, inside
        // the transaction manager — which is why StockConcurrencyTest's 20 threads work.
        assertThatThrownBy(() -> {
            productRepository.save(stale);
            em.flush();                      // the conflict surfaces at flush, not at save()
        }).isInstanceOf(jakarta.persistence.OptimisticLockException.class)
          .hasMessageContaining("expected row count 1 but was 0");

        txManager.rollback(tx);

        // The competing writer's change survived; the stale one was refused, not silently applied.
        em.clear();
        assertThat(productRepository.findById(productId).orElseThrow().getVersion())
                .isGreaterThan(staleVersion);
    }

    private void bumpStockInSeparateTransaction() {
        Thread t = new Thread(() -> {
            TransactionStatus tx = txManager.getTransaction(new DefaultTransactionDefinition());
            Product p = productRepository.findById(productId).orElseThrow();
            p.decrementStock(1);
            productRepository.save(p);
            txManager.commit(tx);
        });
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private int readStock(Connection c) throws Exception {
        try (var st = c.prepareStatement("SELECT stock_quantity FROM products WHERE id = ?")) {
            st.setString(1, productId);
            try (var rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void writeStock(Connection c, int value) throws Exception {
        try (var st = c.prepareStatement("UPDATE products SET stock_quantity = ? WHERE id = ?")) {
            st.setInt(1, value);
            st.setString(2, productId);
            st.executeUpdate();
        }
    }
}

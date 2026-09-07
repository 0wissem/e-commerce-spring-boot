package org.example.productservice.product.infrastructure;

import org.example.productservice.AbstractIntegrationTest;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transaction isolation, demonstrated rather than recited.
 *
 * Each anomaly is reproduced with two REAL concurrent transactions on real Postgres, then the
 * isolation level that prevents it is shown to prevent it. H2 could not do this — the
 * anomalies depend on the engine's actual MVCC behaviour.
 *
 * Transactions are driven manually through PlatformTransactionManager rather than
 * @Transactional, because the whole point is to interleave two of them and control exactly
 * when each reads, writes and commits.
 */
@org.springframework.context.annotation.Import(IsolationLevelTest.IsolationTestConfig.class)
class IsolationLevelTest extends AbstractIntegrationTest {

    @Autowired private PlatformTransactionManager txManager;
    @Autowired private IProductRepository productRepository;
    @Autowired private DataSource dataSource;

    @PersistenceContext private EntityManager em;

    @Autowired private IsolationReporter isolationReporter;

    private String productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID().toString();
        productRepository.save(new Product(productId, "Isolation Subject", new BigDecimal("10.00"), 100));
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteById(productId);
    }

    private TransactionStatus begin(int isolation) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(isolation);
        return txManager.getTransaction(def);
    }

    @Test
    @DisplayName("Postgres never allows a DIRTY READ — even at READ UNCOMMITTED")
    void dirtyReadIsImpossibleInPostgres() throws Exception {
        // Postgres has no true READ UNCOMMITTED: it silently upgrades to READ COMMITTED.
        // So the weakest anomaly — reading another transaction's uncommitted write — simply
        // cannot happen here, whatever you ask for. Worth knowing, because the textbook
        // isolation table implies otherwise and interviewers quote the textbook.
        // Postgres REPORTS whichever level you asked for, so checking the label proves nothing
        // (my first attempt asserted the label and was simply wrong). Assert the BEHAVIOUR:
        // hold an uncommitted write open on one connection and try to read it from another.
        try (Connection writer = dataSource.getConnection();
             Connection reader = dataSource.getConnection()) {

            writer.setAutoCommit(false);
            reader.setAutoCommit(false);
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

            // Writer changes the row and deliberately does NOT commit.
            try (var st = writer.prepareStatement(
                    "UPDATE products SET stock_quantity = 1 WHERE id = ?")) {
                st.setString(1, productId);
                st.executeUpdate();
            }

            int seenByReader;
            try (var st = reader.prepareStatement(
                    "SELECT stock_quantity FROM products WHERE id = ?")) {
                st.setString(1, productId);
                try (var rs = st.executeQuery()) {
                    rs.next();
                    seenByReader = rs.getInt(1);
                }
            }

            // THE POINT: the uncommitted 1 is invisible. Postgres implements READ UNCOMMITTED
            // as READ COMMITTED, so a dirty read cannot happen here no matter what you ask for.
            assertThat(seenByReader)
                    .as("an uncommitted write must never be visible to another transaction")
                    .isEqualTo(100);

            writer.rollback();
            reader.rollback();
        }
    }

    @Test
    @DisplayName("READ COMMITTED allows a NON-REPEATABLE READ: same row, same tx, two values")
    void nonRepeatableReadOccursAtReadCommitted() {
        TransactionStatus reader = begin(TransactionDefinition.ISOLATION_READ_COMMITTED);

        int firstRead = productRepository.findById(productId).orElseThrow().getStockQuantity();
        assertThat(firstRead).isEqualTo(100);

        // A concurrent writer commits a change to the SAME row, in its own connection.
        inSeparateCommittedTransaction(() -> {
            Product p = productRepository.findById(productId).orElseThrow();
            p.decrementStock(40);
            productRepository.save(p);
        });

        // Clear the persistence context, otherwise we would just re-read Hibernate's
        // first-level cache and never touch the database at all.
        clearPersistenceContext();

        int secondRead = productRepository.findById(productId).orElseThrow().getStockQuantity();

        // THE ANOMALY: one transaction, one row, two different answers.
        assertThat(secondRead)
                .as("at READ COMMITTED each statement sees a fresh snapshot")
                .isEqualTo(60)
                .isNotEqualTo(firstRead);

        txManager.rollback(reader);
    }

    @Test
    @DisplayName("REPEATABLE READ freezes the snapshot: the same row reads the same, always")
    void repeatableReadIsStableAcrossTheTransaction() {
        TransactionStatus reader = begin(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        int firstRead = productRepository.findById(productId).orElseThrow().getStockQuantity();
        assertThat(firstRead).isEqualTo(100);

        inSeparateCommittedTransaction(() -> {
            Product p = productRepository.findById(productId).orElseThrow();
            p.decrementStock(40);
            productRepository.save(p);
        });

        clearPersistenceContext();

        int secondRead = productRepository.findById(productId).orElseThrow().getStockQuantity();

        // THE FIX: the snapshot was taken when the transaction began, so the committed change
        // is invisible here. Stable — at the cost of reading data that is already stale.
        assertThat(secondRead)
                .as("REPEATABLE READ pins the snapshot to the start of the transaction")
                .isEqualTo(firstRead);

        txManager.rollback(reader);
    }

    @Test
    @DisplayName("PHANTOM READ at READ COMMITTED: the same range query returns more rows")
    void phantomReadOccursAtReadCommitted() {
        String marker = "phantom-" + UUID.randomUUID();
        TransactionStatus reader = begin(TransactionDefinition.ISOLATION_READ_COMMITTED);

        long firstCount = countByBrand(marker);
        assertThat(firstCount).isZero();

        // Another transaction INSERTS a row that matches the reader's predicate.
        // Note the difference from a non-repeatable read: that was an existing row CHANGING,
        // this is a NEW row APPEARING in a range that was already queried.
        String phantomId = UUID.randomUUID().toString();
        inSeparateCommittedTransaction(() -> {
            Product p = new Product(phantomId, "Phantom", new BigDecimal("1.00"), 1);
            p.setBrand(marker);
            productRepository.save(p);
        });
        clearPersistenceContext();

        long secondCount = countByBrand(marker);

        // THE ANOMALY: same query, same transaction, a row that did not exist before.
        assertThat(secondCount)
                .as("at READ COMMITTED a newly committed row appears mid-transaction")
                .isEqualTo(1);

        txManager.rollback(reader);
        productRepository.deleteById(phantomId);
    }

    @Test
    @DisplayName("Postgres blocks phantoms at REPEATABLE READ — stricter than the SQL standard")
    void phantomReadIsPreventedAtRepeatableRead() {
        String marker = "phantom-" + UUID.randomUUID();
        TransactionStatus reader = begin(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        long firstCount = countByBrand(marker);

        String phantomId = UUID.randomUUID().toString();
        inSeparateCommittedTransaction(() -> {
            Product p = new Product(phantomId, "Phantom", new BigDecimal("1.00"), 1);
            p.setBrand(marker);
            productRepository.save(p);
        });
        clearPersistenceContext();

        long secondCount = countByBrand(marker);

        // WORTH KNOWING FOR INTERVIEWS: the SQL standard PERMITS phantom reads at REPEATABLE
        // READ and says you need SERIALIZABLE to stop them. Postgres implements REPEATABLE
        // READ as true snapshot isolation, so phantoms cannot occur here either. Quoting the
        // standard's table as if it described Postgres is a common and confident error.
        assertThat(secondCount)
                .as("REPEATABLE READ in Postgres is snapshot isolation — no phantoms")
                .isEqualTo(firstCount);

        txManager.rollback(reader);
        productRepository.deleteById(phantomId);
    }

    @Test
    @DisplayName("@Transactional(isolation = …) is the declarative form of the same thing")
    void declarativeIsolationIsHonoured() {
        // Everything above drives transactions by hand to control the interleaving. In
        // application code you would never do that — you declare the level on the method and
        // Spring applies it to the connection. This asserts the annotation really takes effect.
        assertThat(isolationReporter.readCommittedLevel()).isEqualTo("read committed");
        assertThat(isolationReporter.repeatableReadLevel()).isEqualTo("repeatable read");
        assertThat(isolationReporter.serializableLevel()).isEqualTo("serializable");
    }

    private long countByBrand(String brand) {
        return (long) em.createNativeQuery("SELECT COUNT(*) FROM products WHERE brand = :brand")
                .setParameter("brand", brand)
                .getSingleResult();
    }

    /** Runs work in its own transaction on its own connection, and commits it. */
    private void inSeparateCommittedTransaction(Runnable work) {
        Thread t = new Thread(() -> {
            TransactionStatus writer = begin(TransactionDefinition.ISOLATION_READ_COMMITTED);
            work.run();
            txManager.commit(writer);
        });
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Really clears the first-level cache.
     *
     * Without this, a second findById() returns the SAME managed instance from Hibernate's
     * persistence context and never issues SQL — so the test would prove nothing about
     * isolation. (My first version of this method was a no-op and did exactly that.)
     */
    private void clearPersistenceContext() {
        em.clear();
    }

    /**
     * A bean whose only job is to report the isolation level the DATABASE is actually running
     * under, per method. Declared with @Transactional(isolation = …) — the form real code uses.
     *
     * Registered as a @TestConfiguration bean so it is a genuine Spring proxy: calling these
     * methods directly on a `new` instance would bypass the proxy and prove nothing (the
     * self-invocation rule again).
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class IsolationTestConfig {
        @org.springframework.context.annotation.Bean
        IsolationReporter isolationReporter(EntityManager em) {
            return new IsolationReporter(em);
        }
    }

    static class IsolationReporter {
        private final EntityManager em;

        IsolationReporter(EntityManager em) { this.em = em; }

        @org.springframework.transaction.annotation.Transactional(
                isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
        public String readCommittedLevel() { return currentLevel(); }

        @org.springframework.transaction.annotation.Transactional(
                isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
        public String repeatableReadLevel() { return currentLevel(); }

        @org.springframework.transaction.annotation.Transactional(
                isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
        public String serializableLevel() { return currentLevel(); }

        /** Asks the SERVER, not the driver — the only trustworthy source. */
        private String currentLevel() {
            return (String) em.createNativeQuery("SHOW transaction_isolation").getSingleResult();
        }
    }
}

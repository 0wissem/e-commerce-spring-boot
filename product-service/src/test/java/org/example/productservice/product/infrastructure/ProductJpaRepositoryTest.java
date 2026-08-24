package org.example.productservice.product.infrastructure;

import jakarta.persistence.EntityManager;
import org.example.productservice.AbstractIntegrationTest;
import org.example.productservice.product.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INTEGRATION test of the persistence layer against a REAL Postgres (shared
 * Testcontainers instance from AbstractIntegrationTest) with the REAL Flyway schema
 * (search_vector tsvector + GIN). The full-text search can ONLY be tested against real
 * Postgres — H2 has no tsvector operators. That's the core argument for Testcontainers.
 *
 * @Transactional → each test runs in a transaction that rolls back → clean isolation.
 */
@Transactional
class ProductJpaRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ProductJpaRepository repository;
    @Autowired
    private EntityManager em; // jakarta.persistence.EntityManager — to flush/clear the cache

    private Product newProduct(String name, double price, int stock) {
        return new Product(UUID.randomUUID().toString(), name, price, stock);
    }

    @Test
    @DisplayName("save + findById round-trips against real Postgres")
    void saveAndFind() {
        Product saved = repository.save(newProduct("Keyboard", 100.0, 5));
        em.flush();
        em.clear(); // drop the 1st-level cache so findById hits the DB for real

        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("findByNameIgnoreCase is case-insensitive")
    void findByNameIgnoreCase() {
        repository.save(newProduct("Mechanical Keyboard", 100.0, 5));
        em.flush();
        em.clear();

        assertThat(repository.findByNameIgnoreCase("mechanical keyboard")).isPresent();
    }

    @Test
    @DisplayName("full-text search (Postgres tsvector) finds by a word in the name — impossible on H2")
    void fullTextSearch() {
        repository.save(newProduct("Mechanical Keyboard RGB", 120.0, 3));
        repository.save(newProduct("Wireless Mouse", 40.0, 10));
        em.flush();
        em.clear();

        Page<Product> results = repository.search("keyboard", null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getName()).contains("Keyboard");
    }

    @Test
    @DisplayName("weighted search: a name match outranks a description-only match")
    void weightedSearchRanksNameAboveDescription() {
        // "ergonomic" appears in one product's NAME (weight A) and another's DESCRIPTION
        // (weight C). ts_rank must put the name match first.
        Product nameMatch = newProduct("Ergonomic Keyboard", 120.0, 3);

        Product descriptionMatch = newProduct("Standard Mouse", 40.0, 10);
        descriptionMatch.setDescription("A comfortable and ergonomic shape for long sessions");

        repository.save(descriptionMatch); // saved FIRST, so insertion order can't explain the result
        repository.save(nameMatch);
        em.flush();
        em.clear();

        Page<Product> results =
                repository.search("ergonomic", null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getContent().get(0).getName()).isEqualTo("Ergonomic Keyboard");
        assertThat(results.getContent().get(1).getName()).isEqualTo("Standard Mouse");
    }

    @Test
    @DisplayName("search filters on price_amount (numeric), not the deprecated double column")
    void searchFiltersOnNumericPrice() {
        repository.save(newProduct("Cheap Cable", 9.99, 5));
        repository.save(newProduct("Pricey Monitor", 499.00, 2));
        em.flush();
        em.clear();

        Page<Product> results = repository.search(
                null, new BigDecimal("10.00"), null, null, null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getName()).isEqualTo("Pricey Monitor");
    }

    @Test
    @DisplayName("audit timestamps are stamped on persist by the @PrePersist callback")
    void auditTimestampsAreStamped() {
        Product saved = repository.save(newProduct("Webcam", 59.90, 4));
        em.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getSku()).startsWith("SKU-");
    }

    @Test
    @DisplayName("soft delete: @SQLRestriction hides rows whose deleted_at is set")
    void softDeleteHidesRow() {
        Product p = repository.save(newProduct("Keyboard", 100.0, 5));
        p.setDeletedAt(LocalDateTime.now());
        repository.save(p);
        em.flush();
        em.clear(); // force a real SQL SELECT, bypassing the persistence-context cache

        // @SQLRestriction("deleted_at IS NULL") makes Hibernate append the filter →
        // the soft-deleted row is invisible to normal queries.
        assertThat(repository.findById(p.getId())).isEmpty();
    }
}

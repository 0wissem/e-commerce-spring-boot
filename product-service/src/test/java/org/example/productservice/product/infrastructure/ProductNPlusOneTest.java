package org.example.productservice.product.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.example.productservice.AbstractIntegrationTest;
import org.example.productservice.category.domain.Category;
import org.example.productservice.category.domain.ICategoryRepository;
import org.example.productservice.product.domain.Product;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates — and fixes — the classic N+1 query problem on Product.categories
 * (@ManyToMany, LAZY). We count the ACTUAL SQL statements with Hibernate Statistics.
 *
 *   findAll()             → 1 query for products + 1 per product for its lazy categories
 *   findAllWithCategories → 1 query (fetch join via @EntityGraph)
 */
@Transactional
class ProductNPlusOneTest extends AbstractIntegrationTest {

    private static final int PRODUCT_COUNT = 5;

    @Autowired
    private ProductJpaRepository productRepository;
    @Autowired
    private ICategoryRepository categoryRepository;
    @Autowired
    private EntityManager em;
    @Autowired
    private EntityManagerFactory emf;

    @BeforeEach
    void seed() {
        Category cat = categoryRepository.save(new Category(UUID.randomUUID().toString(), "Electronics", "gadgets"));
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            Product p = new Product(UUID.randomUUID().toString(), "Product " + i, 10.0 * (i + 1), 5);
            Set<Category> cats = new HashSet<>();
            cats.add(cat);
            p.setCategories(cats);
            productRepository.save(p);
        }
        em.flush();
        em.clear(); // empty the cache so the queries below actually hit the DB
    }

    private Statistics freshStats() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        return stats;
    }

    @Test
    @DisplayName("findAll() triggers N+1: 1 (products) + 1 per product's lazy categories")
    void demonstratesNPlusOne() {
        Statistics stats = freshStats();

        List<Product> products = productRepository.findAll();
        products.forEach(p -> p.getCategories().size()); // touch the lazy collection (like the mapper does)

        long queries = stats.getPrepareStatementCount();
        // 1 for the products + one categories query PER product = the N+1 smell
        assertThat(queries).isGreaterThanOrEqualTo(1 + PRODUCT_COUNT);
    }

    @Test
    @DisplayName("findAllWithCategories() (@EntityGraph fetch join) avoids N+1: ~1 query")
    void fixWithEntityGraph() {
        Statistics stats = freshStats();

        List<Product> products = productRepository.findAllWithCategories();
        products.forEach(p -> p.getCategories().size()); // already fetched → no extra query

        long queries = stats.getPrepareStatementCount();
        assertThat(products).hasSize(PRODUCT_COUNT);
        assertThat(queries).isLessThanOrEqualTo(2); // the single fetch-joined query
    }
}

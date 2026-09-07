package org.example.productservice.product.infrastructure;

import org.example.productservice.product.domain.Product;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface ProductJpaRepository extends JpaRepository<Product, String> {

    Optional<Product> findByNameIgnoreCase(String name);

    /**
     * PESSIMISTIC read: takes a row-level write lock immediately (`SELECT ... FOR UPDATE`).
     *
     * Contrast with @Version, which takes no lock at all and only DETECTS a conflict when it
     * writes. Here the conflict is PREVENTED — the second transaction blocks at the SELECT
     * until the first commits, then reads the already-updated row.
     *
     *   optimistic  = read freely, fail at write, retry     → cheap when conflicts are rare
     *   pessimistic = queue at read, never fail, never retry → cheaper when they are common
     *
     * The cost is real: the lock is held for the whole transaction, so concurrent callers
     * serialise, and a lock that is never released is a deadlock waiting to happen. Hence the
     * timeout below — without it a blocked request waits forever.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") String id);

    /**
     * N+1 fix: fetch each product's categories in the SAME query via an entity graph
     * (a fetch join), instead of one lazy query per product. DISTINCT removes the row
     * duplication a join over a @ManyToMany produces.
     */
    @EntityGraph(attributePaths = "categories")
    @Query("SELECT DISTINCT p FROM Product p")
    List<Product> findAllWithCategories();

    /**
     * Price filters now read `price_amount` (numeric), not the deprecated `price` double.
     *
     * Results are ordered by ts_rank against the weighted search_vector, so a term matching
     * a product NAME (weight A) outranks the same term appearing only in the DESCRIPTION
     * (weight C). When no query is supplied every row ranks 0 and the ordering falls back
     * to newest-first, which is why created_at is the tiebreaker.
     */
    @Query(
        value = """
            SELECT DISTINCT p.*,
                   CASE WHEN :query IS NULL THEN 0
                        ELSE ts_rank(p.search_vector, plainto_tsquery('english', :query))
                   END AS rank
            FROM products p
            LEFT JOIN product_categories pc ON p.id = pc.product_id
            LEFT JOIN categories c ON pc.category_id = c.id
            WHERE p.deleted_at IS NULL
            AND (:query IS NULL OR p.search_vector @@ plainto_tsquery('english', :query))
            AND (:minPrice IS NULL OR p.price_amount >= :minPrice)
            AND (:maxPrice IS NULL OR p.price_amount <= :maxPrice)
            AND (:brand IS NULL OR p.brand = :brand)
            AND (:categoryId IS NULL OR c.id = :categoryId)
            AND (:inStock IS NULL
                 OR (:inStock = true AND p.stock_quantity > 0)
                 OR (:inStock = false AND p.stock_quantity = 0))
            ORDER BY rank DESC, p.created_at DESC
            """,
        countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM products p
            LEFT JOIN product_categories pc ON p.id = pc.product_id
            LEFT JOIN categories c ON pc.category_id = c.id
            WHERE p.deleted_at IS NULL
            AND (:query IS NULL OR p.search_vector @@ plainto_tsquery('english', :query))
            AND (:minPrice IS NULL OR p.price_amount >= :minPrice)
            AND (:maxPrice IS NULL OR p.price_amount <= :maxPrice)
            AND (:brand IS NULL OR p.brand = :brand)
            AND (:categoryId IS NULL OR c.id = :categoryId)
            AND (:inStock IS NULL
                 OR (:inStock = true AND p.stock_quantity > 0)
                 OR (:inStock = false AND p.stock_quantity = 0))
            """,
        nativeQuery = true
    )
    Page<Product> search(
            @Param("query") String query,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("brand") String brand,
            @Param("categoryId") String categoryId,
            @Param("inStock") Boolean inStock,
            Pageable pageable
    );
}
package org.example.springboot0.product.infrastructure;

import org.example.springboot0.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface ProductJpaRepository extends JpaRepository<Product, String> {

    Optional<Product> findByNameIgnoreCase(String name);

    @Query(
        value = """
            SELECT DISTINCT p.* FROM products p
            LEFT JOIN product_categories pc ON p.id = pc.product_id
            LEFT JOIN categories c ON pc.category_id = c.id
            WHERE p.deleted_at IS NULL
            AND (:query IS NULL OR p.search_vector @@ plainto_tsquery('english', :query))
            AND (:minPrice IS NULL OR p.price >= :minPrice)
            AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            AND (:categoryId IS NULL OR c.id = :categoryId)
            AND (:inStock IS NULL
                 OR (:inStock = true AND p.stock_quantity > 0)
                 OR (:inStock = false AND p.stock_quantity = 0))
            """,
        countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM products p
            LEFT JOIN product_categories pc ON p.id = pc.product_id
            LEFT JOIN categories c ON pc.category_id = c.id
            WHERE p.deleted_at IS NULL
            AND (:query IS NULL OR p.search_vector @@ plainto_tsquery('english', :query))
            AND (:minPrice IS NULL OR p.price >= :minPrice)
            AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            AND (:categoryId IS NULL OR c.id = :categoryId)
            AND (:inStock IS NULL
                 OR (:inStock = true AND p.stock_quantity > 0)
                 OR (:inStock = false AND p.stock_quantity = 0))
            """,
        nativeQuery = true
    )
    Page<Product> search(
            @Param("query") String query,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("categoryId") String categoryId,
            @Param("inStock") Boolean inStock,
            Pageable pageable
    );
}
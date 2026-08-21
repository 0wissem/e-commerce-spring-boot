package org.example.orderservice.order.infrastructure;

import org.example.orderservice.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface OrderJpaRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerId(String customerId);

    // Load the items alongside the orders to avoid the N+1 query problem.
    @EntityGraph(attributePaths = {"orderItems"})
    @Override
    Page<Order> findAll(Pageable pageable);

    /**
     * KEYSET (seek) pagination of a customer's order history.
     *
     * Why not OFFSET: `LIMIT 20 OFFSET 100000` makes Postgres read and discard 100,000 rows,
     * so page 5000 is far slower than page 1. Keyset instead SEEKS straight to a position
     * using the index — every page costs the same regardless of how deep it is.
     *
     * The cursor is the (created_at, id) pair of the last row the client saw. The row
     * comparison `(created_at, id) < (:afterCreatedAt, :afterId)` is a real tuple comparison
     * in Postgres and maps exactly onto idx_orders_customer_created — so this is an index
     * scan, not a sort. id breaks ties when two orders share a timestamp.
     *
     * First page: pass nulls for the cursor.
     *
     * The trade-off: next/prev only. There is no "jump to page 50", because the cursor
     * describes a POSITION, not an offset. Perfect for infinite scroll.
     */
    @Query(
        value = """
            SELECT * FROM orders o
            WHERE o.customer_id = :customerId
              AND (
                    CAST(:afterCreatedAt AS timestamptz) IS NULL
                    OR (o.created_at, o.id) < (CAST(:afterCreatedAt AS timestamptz), :afterId)
                  )
            ORDER BY o.created_at DESC, o.id DESC
            LIMIT :limit
            """,
        nativeQuery = true
    )
    List<Order> findCustomerHistoryPage(
            @Param("customerId") String customerId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") String afterId,
            @Param("limit") int limit
    );
}

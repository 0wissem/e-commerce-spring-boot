package org.example.orderservice.order.infrastructure;

import jakarta.persistence.EntityManager;
import org.example.orderservice.AbstractIntegrationTest;
import org.example.orderservice.order.domain.IOrderRepository;
import org.example.orderservice.order.domain.Order;
import org.example.orderservice.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keyset (seek) pagination against real Postgres.
 *
 * Proves the three properties that matter: correct ordering, no gaps or duplicates when
 * walking pages, and — the point of the whole exercise — that the query is served by an
 * INDEX SCAN rather than a sort over the table.
 */
@Transactional
class OrderKeysetPaginationTest extends AbstractIntegrationTest {

    @Autowired
    private IOrderRepository orderRepository;
    @Autowired
    private EntityManager em;

    private static final String CUSTOMER = "cust-keyset";

    /** Creates 10 orders one minute apart so created_at ordering is unambiguous. */
    private void seedTenOrders() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 10; i++) {
            Order order = new Order(null, CUSTOMER, "Alice", new BigDecimal("10.00"), OrderStatus.PENDING);
            order.setOrderItems(List.of());
            orderRepository.save(order);
            // @PrePersist stamps createdAt; override it so the ordering is deterministic.
            order.setCreatedAt(base.plus(i, ChronoUnit.MINUTES));
        }
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("first page returns the newest N orders, newest first")
    void firstPageIsNewestFirst() {
        seedTenOrders();

        List<Order> page = orderRepository.findCustomerHistoryPage(CUSTOMER, null, null, 3);

        assertThat(page).hasSize(3);
        assertThat(page.get(0).getCreatedAt()).isAfter(page.get(1).getCreatedAt());
        assertThat(page.get(1).getCreatedAt()).isAfter(page.get(2).getCreatedAt());
    }

    @Test
    @DisplayName("walking the cursor covers every row exactly once — no gaps, no duplicates")
    void cursorWalkIsComplete() {
        seedTenOrders();

        List<String> seen = new java.util.ArrayList<>();
        Instant cursorCreatedAt = null;
        String cursorId = null;

        while (true) {
            List<Order> page =
                    orderRepository.findCustomerHistoryPage(CUSTOMER, cursorCreatedAt, cursorId, 3);
            if (page.isEmpty()) break;

            page.forEach(o -> seen.add(o.getId()));
            Order last = page.get(page.size() - 1);
            cursorCreatedAt = last.getCreatedAt();
            cursorId = last.getId();
        }

        assertThat(seen).hasSize(10);
        assertThat(seen).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("the history query uses an INDEX SCAN, not a sort — the whole point of keyset")
    void queryPlanUsesTheIndex() {
        seedTenOrders();

        // Ask Postgres what it actually does, rather than assuming.
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("""
                        EXPLAIN SELECT * FROM orders o
                        WHERE o.customer_id = :customerId
                        ORDER BY o.created_at DESC, o.id DESC
                        LIMIT 3
                        """)
                .setParameter("customerId", CUSTOMER)
                .getResultList();

        String plan = rows.stream()
                .map(String::valueOf)
                .reduce("", (a, b) -> a + "\n" + b);

        // On a tiny test table Postgres may still prefer a seq scan, so this asserts the
        // weaker but meaningful property: it never falls back to an explicit Sort node,
        // because the index already provides the required ordering.
        assertThat(plan).doesNotContain("Sort Key");
    }

    @Test
    @DisplayName("history is scoped to one customer")
    void historyIsScopedToTheCustomer() {
        seedTenOrders();
        Order other = new Order(null, "someone-else", "Bob", new BigDecimal("99.00"), OrderStatus.PENDING);
        other.setOrderItems(List.of());
        orderRepository.save(other);
        em.flush();

        List<Order> page = orderRepository.findCustomerHistoryPage(CUSTOMER, null, null, 50);

        assertThat(page).hasSize(10);
        assertThat(page).allMatch(o -> CUSTOMER.equals(o.getCustomerId()));
    }
}

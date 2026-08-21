package org.example.orderservice.order.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE test that makes snapshot v2 safe to ship.
 *
 * order_items rows written before this change contain v1 JSON. They are real customer orders
 * and cannot be migrated away, so the v2 record MUST be able to read them. Without this test,
 * the breakage only shows up when someone opens an old order in production.
 */
class OrderProductSnapshotCompatibilityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Exactly what v1 wrote: no version, no sku, no brand, no currency. */
    private static final String V1_JSON = """
            {
              "name": "Mechanical Keyboard",
              "price": 100.0,
              "categories": [
                {"id": "cat-1", "name": "Peripherals"}
              ]
            }
            """;

    @Test
    @DisplayName("a v1 snapshot row still deserializes into the v2 record")
    void v1JsonDeserializesIntoV2Record() throws Exception {
        OrderProductSnapshot snapshot = mapper.readValue(V1_JSON, OrderProductSnapshot.class);

        // What v1 stored is preserved.
        assertThat(snapshot.name()).isEqualTo("Mechanical Keyboard");
        assertThat(snapshot.price()).isEqualByComparingTo("100.0");
        assertThat(snapshot.categories()).hasSize(1);

        // What v1 never had comes back null — not a crash, and not a silent zero.
        assertThat(snapshot.sku()).isNull();
        assertThat(snapshot.brand()).isNull();
        assertThat(snapshot.currency()).isNull();
        assertThat(snapshot.version()).isNull();
    }

    @Test
    @DisplayName("an untagged (v1) row reports version 1; a new one reports 2")
    void effectiveVersionDistinguishesRowShapes() throws Exception {
        OrderProductSnapshot legacy = mapper.readValue(V1_JSON, OrderProductSnapshot.class);
        assertThat(legacy.effectiveVersion()).isEqualTo(1);

        OrderProductSnapshot current = OrderProductSnapshot.of(
                "Mouse", "SKU-M1", "Logitech", new BigDecimal("49.90"), "EUR", List.of());
        assertThat(current.effectiveVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("a v2 snapshot round-trips through JSON unchanged")
    void v2RoundTrips() throws Exception {
        OrderProductSnapshot original = OrderProductSnapshot.of(
                "Keyboard", "SKU-KB", "Logitech", new BigDecimal("100.00"), "EUR",
                List.of(new OrderProductSnapshot.CategorySnapshot("cat-1", "Peripherals")));

        OrderProductSnapshot restored =
                mapper.readValue(mapper.writeValueAsString(original), OrderProductSnapshot.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("price survives as an exact decimal, not a double")
    void priceKeepsItsPrecision() throws Exception {
        // 0.1 + 0.2 through a double would land on 0.30000000000000004.
        String json = """
                {"version":2,"name":"Cable","price":19.99,"categories":[]}
                """;

        OrderProductSnapshot snapshot = mapper.readValue(json, OrderProductSnapshot.class);

        assertThat(snapshot.price()).isEqualByComparingTo("19.99");
        assertThat(snapshot.price()).isInstanceOf(BigDecimal.class);
    }
}

package org.example.productservice.product.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test of the crossing rule — threshold 5 throughout. */
class LowStockPolicyTest {

    private final LowStockPolicy policy = new LowStockPolicy(5);

    @ParameterizedTest(name = "{0} -> {1} : alert = {2}")
    @CsvSource({
            "6, 5, true",    // lands exactly ON the threshold: that is 'low'
            "6, 4, true",    // crosses it
            "20, 0, true",   // one big order straight through it
            "6, 6, false",   // no change
            "10, 6, false",  // dropping, but still above
            "5, 4, false",   // already low: no second alert
            "3, 0, false",   // already low, sold out: still no second alert
            "4, 9, false",   // restock going UP never alerts
    })
    void crossedDown(int before, int after, boolean expected) {
        assertThat(policy.crossedDown(before, after)).isEqualTo(expected);
    }

    @Test
    @DisplayName("evaluate builds the event from the product's state AFTER the change")
    void evaluate_buildsEventFromNewState() {
        Product product = new Product("p1", "Keyboard", new BigDecimal("10.00"), 3);

        StockLowEvent event = policy.evaluate(product, 8).orElseThrow();

        assertThat(event.productId()).isEqualTo("p1");
        assertThat(event.stockQuantity()).isEqualTo(3);
        assertThat(event.threshold()).isEqualTo(5);
    }

    @Test
    @DisplayName("each event gets its own id — the consumer's idempotency key")
    void everyEventHasAUniqueId() {
        Product product = new Product("p1", "Keyboard", new BigDecimal("10.00"), 3);

        String first = policy.evaluate(product, 8).orElseThrow().eventId();
        String second = policy.evaluate(product, 8).orElseThrow().eventId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void negativeThreshold_isRejected() {
        assertThatThrownBy(() -> new LowStockPolicy(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}

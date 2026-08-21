package org.example.productservice.product.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the money contract after the double -> BigDecimal migration.
 * No Spring, no database — pure domain arithmetic.
 */
class ProductMoneyTest {

    @Test
    @DisplayName("the double arithmetic this replaced was genuinely wrong")
    void doubleArithmeticIsInexact() {
        // This is the bug, demonstrated rather than asserted from memory.
        double sum = 0.1 + 0.2;
        assertThat(sum).isNotEqualTo(0.3);
        assertThat(sum).isEqualTo(0.30000000000000004);

        // The same sum in BigDecimal is exact.
        BigDecimal exact = new BigDecimal("0.10").add(new BigDecimal("0.20"));
        assertThat(exact).isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("finalPrice applies 19% VAT exactly, rounded half-up to 2 decimals")
    void finalPriceIsExact() {
        Product product = new Product("p1", "Keyboard", new BigDecimal("100.00"), 1);

        // 100.00 * 1.19 = 119.00 exactly — no tolerance needed.
        assertThat(product.getFinalPrice()).isEqualByComparingTo("119.00");
    }

    @Test
    @DisplayName("finalPrice rounds half-up at the 2nd decimal, never truncates")
    void finalPriceRoundsHalfUp() {
        // 10.99 * 1.19 = 13.0781 -> 13.08 (half-up), not 13.07 (truncated).
        Product product = new Product("p2", "Cable", new BigDecimal("10.99"), 1);

        assertThat(product.getFinalPrice()).isEqualByComparingTo("13.08");
    }

    @Test
    @DisplayName("a price is normalised to 2 decimal places on the way in")
    void priceIsNormalisedToScaleTwo() {
        Product product = new Product("p3", "Mouse", new BigDecimal("19.999"), 1);

        // Matches NUMERIC(12,2) in the database — the entity cannot hold a value
        // the column could not store.
        assertThat(product.getPriceAmount()).isEqualByComparingTo("20.00");
        assertThat(product.getPriceAmount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("the legacy double column is kept in sync during the expand/contract overlap")
    void legacyColumnIsDualWritten() {
        Product product = new Product("p4", "Monitor", new BigDecimal("249.50"), 1);

        // An older instance still reading `price` sees the same value until the
        // contract migration drops the column.
        assertThat(product.getPrice()).isEqualTo(249.50d);
    }

    @Test
    @DisplayName("currency defaults to EUR — an amount without a currency is not money")
    void currencyDefaultsToEur() {
        Product product = new Product("p5", "Webcam", new BigDecimal("59.90"), 1);

        assertThat(product.getCurrency()).isEqualTo("EUR");
    }
}

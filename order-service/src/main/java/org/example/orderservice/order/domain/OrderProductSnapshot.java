package org.example.orderservice.order.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot v2.
 *
 * Freezes what the customer actually ordered, so the order stays truthful when the product
 * is later renamed, repriced, or soft-deleted in product-service.
 *
 * BACKWARD COMPATIBILITY — the important part:
 * Rows written by v1 contain only {name, price, categories}. They must keep deserializing.
 * Two things make that work:
 *   1. @JsonIgnoreProperties(ignoreUnknown = true) — already present in v1, which is why a
 *      v1 READER also tolerates v2 JSON. Forward and backward compatibility both rely on it.
 *   2. Every field added in v2 is a NULLABLE reference type. A v1 row simply yields null for
 *      sku/brand/currency — never an exception. This is why `price` is BigDecimal and not a
 *      primitive: a missing number in a primitive would silently become 0.
 *
 * `version` is null on v1 rows and 2 on rows written from now on. Readers branch on it rather
 * than guessing from which fields happen to be populated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderProductSnapshot(
        Integer version,
        String name,
        String sku,
        String brand,
        BigDecimal price,
        String currency,
        List<CategorySnapshot> categories
) {
    public static final int CURRENT_VERSION = 2;

    /** Builds a v2 snapshot. */
    public static OrderProductSnapshot of(String name, String sku, String brand,
                                          BigDecimal price, String currency,
                                          List<CategorySnapshot> categories) {
        return new OrderProductSnapshot(CURRENT_VERSION, name, sku, brand, price, currency, categories);
    }

    /** v1 rows carry no version tag. */
    public int effectiveVersion() {
        return version == null ? 1 : version;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategorySnapshot(String id, String name) {}
}

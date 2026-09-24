package org.example.productservice.product.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The event contract published on the {@code stock.low} topic.
 *
 * Every field is here for a reason a consumer depends on:
 * - {@code eventId}       the IDEMPOTENCY key. Kafka delivers at-least-once, so a consumer WILL
 *                         see the same event twice (producer retry, consumer crash before commit).
 *                         It dedupes on this id, never on the payload.
 * - {@code schemaVersion} lets the contract evolve. Consumers are tolerant readers: they ignore
 *                         fields they don't know, so ADDING a field is not a breaking change.
 *                         Renaming or removing one is — that is when the version is bumped.
 * - {@code productId}     also the message KEY, so every event for one product lands on the same
 *                         partition and is consumed in order.
 * - {@code occurredAt}    when it happened, not when it was consumed (consumer lag separates them).
 *
 * There is deliberately NO shared jar between services: notification-service owns its own copy of
 * this shape. A shared library would couple both deploys to one release — the distributed monolith.
 */
public record StockLowEvent(
        String eventId,
        int schemaVersion,
        String productId,
        String productName,
        int stockQuantity,
        int threshold,
        Instant occurredAt) {

    public static final int SCHEMA_VERSION = 1;

    public static StockLowEvent of(String productId, String productName, int stockQuantity, int threshold) {
        return new StockLowEvent(UUID.randomUUID().toString(), SCHEMA_VERSION,
                productId, productName, stockQuantity, threshold, Instant.now());
    }
}

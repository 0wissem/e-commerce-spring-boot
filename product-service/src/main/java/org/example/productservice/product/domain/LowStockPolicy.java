package org.example.productservice.product.domain;

import java.util.Optional;

/**
 * Decides whether a stock change deserves a low-stock alert. Pure Java — no Spring, no Kafka.
 *
 * The rule is a CROSSING, not a level:
 *
 *     before > threshold  AND  after <= threshold
 *
 * "Publish whenever stock is at or below the threshold" would fire on every sale of an item that
 * is already low: 5 -> 4 -> 3 -> 2 would send four alerts for one situation. The crossing fires
 * exactly once on the way down. Restocking above the threshold re-arms it.
 */
public class LowStockPolicy {

    private final int threshold;

    public LowStockPolicy(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Low-stock threshold must not be negative");
        }
        this.threshold = threshold;
    }

    public boolean crossedDown(int stockBefore, int stockAfter) {
        return stockBefore > threshold && stockAfter <= threshold;
    }

    public Optional<StockLowEvent> evaluate(Product product, int stockBefore) {
        if (!crossedDown(stockBefore, product.getStockQuantity())) {
            return Optional.empty();
        }
        return Optional.of(StockLowEvent.of(
                product.getId(), product.getName(), product.getStockQuantity(), threshold));
    }

    public int threshold() {
        return threshold;
    }
}

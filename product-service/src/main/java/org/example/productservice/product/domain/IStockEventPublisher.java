package org.example.productservice.product.domain;

/**
 * Port: "tell the outside world stock ran low". The domain does not know it is Kafka.
 *
 * Contract for adapters: calling this inside a transaction must NOT make the event visible before
 * that transaction commits. A rolled-back decrement must never produce an alert.
 */
public interface IStockEventPublisher {
    void publish(StockLowEvent event);
}

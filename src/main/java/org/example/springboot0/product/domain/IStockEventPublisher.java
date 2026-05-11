package org.example.springboot0.product.domain;

public interface IStockEventPublisher {
    void publish(StockUpdatedEvent event);
}

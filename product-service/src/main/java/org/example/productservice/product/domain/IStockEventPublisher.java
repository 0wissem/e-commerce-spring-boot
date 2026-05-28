package org.example.productservice.product.domain;

public interface IStockEventPublisher {
    void publish(StockUpdatedEvent event);
}

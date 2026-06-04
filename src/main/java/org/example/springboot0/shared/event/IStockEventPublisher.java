package org.example.springboot0.shared.event;

public interface IStockEventPublisher {
    void publish(StockUpdatedEvent event);
}
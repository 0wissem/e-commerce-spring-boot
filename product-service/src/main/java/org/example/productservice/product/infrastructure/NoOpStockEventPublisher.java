package org.example.productservice.product.infrastructure;

import org.example.productservice.product.domain.IStockEventPublisher;
import org.example.productservice.product.domain.StockUpdatedEvent;
import org.springframework.stereotype.Component;

@Component
public class NoOpStockEventPublisher implements IStockEventPublisher {

    @Override
    public void publish(StockUpdatedEvent event) {
        // no-op — Kafka not used in product-service yet
    }
}
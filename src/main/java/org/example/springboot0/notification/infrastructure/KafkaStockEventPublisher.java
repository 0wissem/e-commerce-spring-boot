package org.example.springboot0.notification.infrastructure;

import org.example.springboot0.shared.event.IStockEventPublisher;
import org.example.springboot0.shared.event.StockUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaStockEventPublisher implements IStockEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaStockEventPublisher.class);
    public static final String TOPIC = "stock.updated";

    private final KafkaTemplate<String, StockUpdatedEvent> kafkaTemplate;

    public KafkaStockEventPublisher(KafkaTemplate<String, StockUpdatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(StockUpdatedEvent event) {
        kafkaTemplate.send(TOPIC, event.productId(), event);
        log.info("Published stock event for product: {}", event.productName());
    }
}

package org.example.springboot0.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class StockEventProducer {

    private static final Logger log = LoggerFactory.getLogger(StockEventProducer.class);
    public static final String TOPIC = "stock.updated";

    private final KafkaTemplate<String, StockUpdatedEvent> kafkaTemplate;

    public StockEventProducer(KafkaTemplate<String, StockUpdatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(StockUpdatedEvent event) {
        kafkaTemplate.send(TOPIC, event.productName(), event);
        log.info("Published stock event for product: {}", event.productName());
    }
}
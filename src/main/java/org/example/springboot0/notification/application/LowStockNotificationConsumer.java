package org.example.springboot0.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LowStockNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(LowStockNotificationConsumer.class);

    private final LowStockNotificationService notificationService;

    public LowStockNotificationConsumer(LowStockNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = StockEventProducer.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(StockUpdatedEvent event) {
        log.info("Received stock event for product: {}", event.productName());
        notificationService.notifyIfLowStock(event.productName(), event.stockQuantity());
    }
}

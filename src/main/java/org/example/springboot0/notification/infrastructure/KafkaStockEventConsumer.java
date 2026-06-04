package org.example.springboot0.notification.infrastructure;

import org.example.springboot0.notification.application.LowStockAlertUseCase;
import org.example.springboot0.shared.event.StockUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaStockEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaStockEventConsumer.class);

    private final LowStockAlertUseCase lowStockAlertUseCase;

    public KafkaStockEventConsumer(LowStockAlertUseCase lowStockAlertUseCase) {
        this.lowStockAlertUseCase = lowStockAlertUseCase;
    }

    @KafkaListener(topics = KafkaStockEventPublisher.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(StockUpdatedEvent event) {
        log.info("Received stock event for product: {}", event.productName());
        lowStockAlertUseCase.handleStockUpdated(event.productName(), event.stockQuantity());
    }
}

package org.example.springboot0.shared.outbox.infrastructure;

import org.example.springboot0.shared.outbox.domain.IOutboxEventRepository;
import org.example.springboot0.shared.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final IOutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(IOutboxEventRepository outboxRepository,
                           @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findPending();
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(OutboxKafkaConfig.TOPIC, event.getId(), event.getPayload()).get();
                event.markSent();
                outboxRepository.save(event);
                log.info("Published outbox event: {} - {}", event.getEventType(), event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {} - {}", event.getEventType(), event.getId(), e);
            }
        }
    }
}
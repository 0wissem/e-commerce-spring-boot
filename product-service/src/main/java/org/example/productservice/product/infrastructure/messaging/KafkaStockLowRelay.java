package org.example.productservice.product.infrastructure.messaging;

import org.example.productservice.product.domain.StockLowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends a committed low-stock event to Kafka.
 *
 * AFTER_COMMIT: Spring calls this only if the transaction that raised the event committed. A
 * rolled-back attempt (including every losing optimistic-lock retry in StockService) never gets
 * here, so it never alerts. fallbackExecution = true also runs it when there was no transaction.
 *
 * KEY = productId. Kafka hashes the key to pick the partition, so all events for one product sit
 * on one partition, in order. Keyless events would be spread round-robin and could be consumed
 * out of order.
 *
 * THE GAP THIS DOES NOT CLOSE (say it before an interviewer does): the DB commit and the Kafka
 * send are still two writes. If the process dies between them, or Kafka stays unreachable past
 * the retries, the alert is lost. For a low-stock alert that is an accepted trade-off — the next
 * restock-and-sell cycle alerts again. For money or orders it would not be, and the fix is the
 * Outbox pattern this project already used for the product sync: write the event to a table in
 * the SAME transaction, relay it from there.
 */
@Component
public class KafkaStockLowRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaStockLowRelay.class);

    private final KafkaTemplate<String, StockLowEvent> kafkaTemplate;
    private final String topic;

    public KafkaStockLowRelay(KafkaTemplate<String, StockLowEvent> kafkaTemplate,
                              @Value("${app.kafka.topics.stock-low}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Never throws. The stock change is ALREADY committed when this runs; failing the HTTP call
     * now would tell order-service "the decrement failed" when it did not, and order-service
     * would compensate a reservation that actually happened. Log it and let the request succeed.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStockLow(StockLowEvent event) {
        try {
            kafkaTemplate.send(topic, event.productId(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Low-stock event {} for product {} NOT delivered to Kafka",
                                    event.eventId(), event.productId(), failure);
                        } else {
                            log.info("Low-stock event {} for product {} -> {}-{}@{}",
                                    event.eventId(), event.productId(), topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException e) {
            // send() itself throws when metadata can't be fetched within max.block.ms (broker down).
            log.error("Low-stock event {} for product {} could not be handed to the producer",
                    event.eventId(), event.productId(), e);
        }
    }
}

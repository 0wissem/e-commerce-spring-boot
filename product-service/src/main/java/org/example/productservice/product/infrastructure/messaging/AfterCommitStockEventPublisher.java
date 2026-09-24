package org.example.productservice.product.infrastructure.messaging;

import org.example.productservice.product.domain.IStockEventPublisher;
import org.example.productservice.product.domain.StockLowEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adapter for the {@link IStockEventPublisher} port — replaces the old NoOp.
 *
 * It does not talk to Kafka. It drops the event on Spring's in-memory event bus, where
 * {@link KafkaStockLowRelay} picks it up only once the surrounding transaction has COMMITTED.
 * Two small classes instead of one, because "defer until commit" and "send to Kafka" are two
 * separate decisions: swap Kafka for SQS later and only the relay changes.
 *
 * Why not send straight to Kafka here? This method runs INSIDE the transaction:
 *
 *   send, then commit fails (optimistic-lock conflict at flush)  -> alert for a sale that never happened
 *   commit, then send                                             -> correct, and what the relay does
 */
@Component
public class AfterCommitStockEventPublisher implements IStockEventPublisher {

    private final ApplicationEventPublisher applicationEvents;

    public AfterCommitStockEventPublisher(ApplicationEventPublisher applicationEvents) {
        this.applicationEvents = applicationEvents;
    }

    @Override
    public void publish(StockLowEvent event) {
        applicationEvents.publishEvent(event);
    }
}

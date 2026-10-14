package org.example.notificationservice.notification.api;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.notificationservice.notification.application.HandlingOutcome;
import org.example.notificationservice.notification.application.INotificationService;
import org.example.notificationservice.notification.application.InvalidEventException;
import org.example.notificationservice.notification.application.dto.StockLowEvent;
import org.example.notificationservice.notification.infrastructure.messaging.FailureHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * The Kafka INBOUND ADAPTER — it lives in api/ next to the REST controller because it plays the
 * same role: translate an external trigger (an HTTP request there, a record here) into a call on
 * the application service. Nothing below this class knows Kafka exists.
 *
 * It has no try/catch on purpose. Throwing is how it tells the container "not handled", and the
 * retry configuration decides what happens next.
 *
 * RETRIES ARE NON-BLOCKING (@RetryableTopic). Measured on the cluster with SMTP down, the earlier
 * blocking backoff held the consumer thread for 1s + 2s + 4s PER failing record: throughput fell
 * from ~50 events in under 20s to 0.27 events/s, and healthy products' alerts queued behind broken
 * ones (head-of-line blocking). Now a failure is forwarded to stock.low-retry and the main topic
 * keeps flowing; the retry topic serves the delay, and exhausted records land in stock.low.DLT.
 */
@Component
public class StockLowEventListener {

    private static final Logger log = LoggerFactory.getLogger(StockLowEventListener.class);

    private final INotificationService notificationService;
    private final MeterRegistry meterRegistry;

    public StockLowEventListener(INotificationService notificationService, MeterRegistry meterRegistry) {
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * concurrency = 3 threads, one per partition. Records of one partition are still handled one
     * at a time, in order — so two events for the same product (same key, same partition) are
     * never processed in parallel.
     *
     * idIsGroup = false — a gotcha found on the running stack: by default the listener {@code id}
     * silently REPLACES the configured group id, so the group was "stock-low-listener" instead of
     * "notification-service". Renaming the id later would have created a brand-new group that
     * re-reads the topic from the beginning.
     */
    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts}",
            // Spring Kafka 4 dropped the spring-retry @Backoff in favour of its own annotation;
            // the *String attributes are the ones that accept a property placeholder.
            backOff = @BackOff(delayString = "${app.kafka.retry.initial-interval-ms}",
                    multiplierString = "${app.kafka.retry.multiplier}"),
            retryTopicSuffix = "-retry",
            numPartitions = "${app.kafka.topics.partitions}",
            replicationFactor = "${app.kafka.topics.replicas}",
            dltTopicSuffix = ".DLT",
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            // Permanent failures must not consume the retry budget — they go straight to the DLT.
            exclude = { InvalidEventException.class,
                    org.springframework.kafka.support.serializer.DeserializationException.class })
    @KafkaListener(id = "stock-low-listener",
            idIsGroup = false,
            topics = "${app.kafka.topics.stock-low}",
            concurrency = "${app.kafka.listener.concurrency}")
    public void onStockLow(@Payload StockLowEvent event,
                           @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                           @Header(KafkaHeaders.OFFSET) long offset) {
        log.debug("Received stock.low event {} from partition {} offset {}", event.eventId(), partition, offset);
        HandlingOutcome outcome = notificationService.handleStockLow(event);
        meterRegistry.counter("notifications.stock_low.handled", "outcome", outcome.name()).increment();
    }

    /**
     * The dead-letter "parking lot" — the end of the line, reached when the retry topics are
     * exhausted or the failure was permanent. Nothing is retried from here automatically: a human
     * reads the reason, fixes the cause, and replays if needed. This method only makes the failure
     * VISIBLE — an error log with the reason Spring stored in the headers, and a counter to alert on.
     *
     * @DltHandler, not a second @KafkaListener: it belongs to the same retry configuration, so
     * Spring routes exhausted records here and never applies retries or another DLT hop to it. A
     * plain listener on the DLT topic would be handled by the global error handler, which would
     * republish a failure back to the SAME dead-letter topic — an endless loop.
     *
     * Takes the raw record: a record that failed to deserialize has only its original bytes.
     */
    @DltHandler
    public void onDeadLetter(ConsumerRecord<?, ?> record) {
        meterRegistry.counter("notifications.stock_low.dead_letters").increment();
        log.error("DEAD LETTER key={} from={} reason=[{}: {}] payload={}",
                record.key(), header(record, KafkaHeaders.ORIGINAL_TOPIC),
                header(record, FailureHeaders.FAILURE_CLASS),
                header(record, FailureHeaders.FAILURE_MESSAGE), value(record));
    }

    private static String value(ConsumerRecord<?, ?> record) {
        Object value = record.value();
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value);
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}

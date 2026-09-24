package org.example.notificationservice.notification.api;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.notificationservice.notification.application.HandlingOutcome;
import org.example.notificationservice.notification.application.INotificationService;
import org.example.notificationservice.notification.application.dto.StockLowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
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
 * It has no try/catch on purpose. Throwing is how it tells the container "not handled": the offset
 * is not committed and the error handler decides between retry and the dead-letter topic.
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
     * The dead-letter "parking lot". Nothing is retried from here automatically — a human reads the
     * reason, fixes the cause, and replays if needed. This listener only makes the failure VISIBLE:
     * an error log with the reason Spring stored in the headers, and a counter to alert on.
     *
     * It reads raw Strings, overriding the JSON deserializer: a record that could not be parsed the
     * first time would fail again here and have nowhere left to go.
     */
    @KafkaListener(id = "stock-low-dlt-listener",
            topics = "${app.kafka.topics.stock-low-dlt}",
            groupId = "notification-service-dlt",
            properties = {
                    "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            })
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        meterRegistry.counter("notifications.stock_low.dead_letters").increment();
        log.error("DEAD LETTER key={} reason=[{}: {}] payload={}",
                record.key(), header(record, KafkaHeaders.DLT_EXCEPTION_FQCN),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE), record.value());
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}

package org.example.notificationservice.notification.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.notificationservice.notification.application.InvalidEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What happens when handling a record FAILS — the part of Kafka that separates a demo from a system.
 *
 * <pre>
 *   record ──> listener throws
 *                 │
 *                 ├─ permanent? (bad JSON, invalid event) ──────────────> stock.low.DLT  (no retries)
 *                 │
 *                 └─ transient? (SMTP down) ─> retry 1s, 2s, 4s ─> still failing ─> stock.low.DLT
 * </pre>
 *
 * Why a dead-letter topic at all: Kafka consumes a partition strictly in order. Without somewhere
 * to put a record that can never succeed, the consumer retries it forever and every event behind
 * it on that partition waits — one bad message halts alerts for a third of the products.
 *
 * Boot's auto-configured listener container factory picks up the {@link DefaultErrorHandler} bean
 * on its own; no custom factory is needed.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> deadLetterTemplate,
            @Value("${app.kafka.topics.stock-low-dlt}") String deadLetterTopic,
            @Value("${app.kafka.retry.initial-interval-ms}") long initialIntervalMs,
            @Value("${app.kafka.retry.multiplier}") double multiplier,
            @Value("${app.kafka.retry.max-retries}") int maxRetries) {

        // Same partition number in the DLT as the original: per-product ordering survives in the
        // DLT too, which matters if someone replays it. Needs the DLT to have >= as many partitions.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate,
                (record, failure) -> {
                    log.error("Giving up on {}-{}@{} (key {}): {} -> {}", record.topic(), record.partition(),
                            record.offset(), record.key(), failure.getMessage(), deadLetterTopic);
                    return new TopicPartition(deadLetterTopic, record.partition());
                });

        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxAttempts(maxRetries);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // DeserializationException is already on Spring's not-retryable list (bad bytes stay bad).
        // An event that parsed but is unusable is just as permanent.
        handler.addNotRetryableExceptions(InvalidEventException.class);
        handler.setRetryListeners((record, failure, attempt) ->
                log.warn("Attempt {} failed for {}-{}@{}: {}", attempt, record.topic(),
                        record.partition(), record.offset(), failure.getMessage()));
        return handler;
    }

    /**
     * The DLT producer has to cope with two very different payloads:
     * - a record that failed DESERIALIZATION: we only have its original raw bytes -> write them
     *   back byte-for-byte, so the DLT shows exactly what was received;
     * - a record that failed PROCESSING: it is a StockLowEvent object -> write it as JSON.
     * One serializer per type, picked at send time. Order matters: first assignable match wins.
     */
    @Bean
    public KafkaTemplate<Object, Object> deadLetterTemplate(KafkaProperties properties) {
        Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>();
        byType.put(byte[].class, new ByteArraySerializer());
        byType.put(String.class, new StringSerializer());
        byType.put(Object.class, jsonWithoutTypeHeaders());

        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                properties.buildProducerProperties(),
                new DelegatingByTypeSerializer(byType, true),
                new DelegatingByTypeSerializer(byType, true));
        return new KafkaTemplate<>(factory);
    }

    private static JacksonJsonSerializer<Object> jsonWithoutTypeHeaders() {
        JacksonJsonSerializer<Object> json = new JacksonJsonSerializer<>();
        json.setAddTypeInfo(false);
        return json;
    }

    /** The consumer owns its dead-letter topic; the source topic belongs to product-service. */
    @Bean
    public NewTopic stockLowDeadLetterTopic(@Value("${app.kafka.topics.stock-low-dlt}") String name,
                                           @Value("${app.kafka.topics.partitions}") int partitions,
                                           @Value("${app.kafka.topics.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}

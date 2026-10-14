package org.example.notificationservice.notification.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What happens when handling a record FAILS — the part of Kafka that separates a demo from a system.
 *
 * <pre>
 *   stock.low ──> listener throws
 *                   │
 *                   ├─ permanent? (bad JSON, invalid event) ─────────────────> stock.low.DLT
 *                   │
 *                   └─ transient? (SMTP down) ──> stock.low-retry ──exhausted──> stock.low.DLT
 * </pre>
 *
 * The retries are NON-BLOCKING: {@code @RetryableTopic} on the listener forwards the failure to a
 * retry topic instead of sleeping on the consumer thread. This was not the first design — the
 * blocking version was measured on the cluster and cost 7s of consumer thread per failing record,
 * stalling every healthy event behind it. See docs/low-stock-alerts.md.
 *
 * There is deliberately NO DefaultErrorHandler bean any more: the retry-topic configuration owns
 * error handling for this listener, and the exhausted records are delivered to the @DltHandler.
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * The template the retry machinery uses to forward records — @Primary because
     * {@code @RetryableTopic} resolves a single KafkaOperations bean and Boot auto-configures one too.
     *
     * It must cope with two very different payloads:
     * - a record that failed DESERIALIZATION: only the original raw bytes exist -> write them back
     *   byte-for-byte, so the DLT shows exactly what was received;
     * - a record that failed PROCESSING: a StockLowEvent object -> write it as JSON.
     * One serializer per type, picked at send time. Order matters: first assignable match wins.
     */
    @Bean
    @Primary
    public KafkaTemplate<Object, Object> retryAndDltTemplate(KafkaProperties properties) {
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

    /**
     * The consumer owns its dead-letter topic; the source topic belongs to product-service.
     * Declared explicitly so it keeps 3 partitions — the DLT mirrors the source partitioning, which
     * keeps per-product ordering intact for a replay.
     */
    @Bean
    public NewTopic stockLowDeadLetterTopic(@Value("${app.kafka.topics.stock-low-dlt}") String name,
                                           @Value("${app.kafka.topics.partitions}") int partitions,
                                           @Value("${app.kafka.topics.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}

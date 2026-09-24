package org.example.notificationservice.notification.api;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.notificationservice.AbstractIntegrationTest;
import org.example.notificationservice.notification.domain.INotificationSender;
import org.example.notificationservice.notification.domain.Notification;
import org.example.notificationservice.notification.domain.NotificationStatus;
import org.example.notificationservice.notification.infrastructure.NotificationJpaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The consumer end to end: raw JSON on a real broker -> listener -> service -> real Postgres.
 *
 * Messages are produced as plain Strings, byte-for-byte what product-service puts on the wire —
 * not as Java objects — so a contract mismatch would show up here. Only the email sender is a
 * mock, so failures can be injected on demand (the real SMTP adapter has its own test).
 *
 * Retries are shortened by AbstractIntegrationTest: 100ms then 200ms, i.e. 3 attempts in total.
 */
class StockLowConsumerIntegrationTest extends AbstractIntegrationTest {

    private static final String TOPIC = "stock.low";
    private static final String DLT = "stock.low.DLT";
    private static final int TOTAL_ATTEMPTS = 3;   // 1 delivery + max-retries=2
    private static final Duration WAIT = Duration.ofSeconds(30);

    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> dltReader;

    @MockitoBean private INotificationSender sender;
    @Autowired private NotificationJpaRepository notifications;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private KafkaListenerEndpointRegistry listeners;

    @BeforeAll
    static void openClients() {
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        dltReader = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    @AfterAll
    static void closeClients() {
        producer.close();
        dltReader.close();
    }

    @Test
    @DisplayName("the listener joins the configured consumer group, not a group named after its id")
    void listenerUsesConfiguredConsumerGroup() {
        MessageListenerContainer container = listeners.getListenerContainer("stock-low-listener");
        assertThat(container).isNotNull();
        assertThat(container.getGroupId()).isEqualTo("notification-service");
    }

    @Test
    @DisplayName("a valid event is stored and notified exactly once")
    void validEvent_isNotifiedOnce() {
        String eventId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        send(productId, eventJson(eventId, productId));

        Notification n = awaitStatus(eventId, NotificationStatus.SENT);
        assertThat(n.getProductId()).isEqualTo(productId);
        assertThat(n.getStockQuantity()).isEqualTo(4);
        assertThat(n.getRecipient()).isEqualTo("stock-team@test.local");
        assertThat(n.getAttempts()).isEqualTo(1);
        verify(sender, times(1)).send(any());
    }

    @Test
    @DisplayName("IDEMPOTENCY: the same event delivered twice -> one row, one email")
    void duplicateDelivery_notifiesOnce() {
        String eventId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        double duplicatesBefore = handledCount("DUPLICATE");

        send(productId, eventJson(eventId, productId));
        send(productId, eventJson(eventId, productId));   // e.g. a producer retry after a lost ack

        await().atMost(WAIT).until(() -> handledCount("DUPLICATE") > duplicatesBefore);
        assertThat(notifications.findAll().stream().filter(x -> x.getEventId().equals(eventId))).hasSize(1);
        verify(sender, times(1)).send(any());
    }

    @Test
    @DisplayName("TRANSIENT failure: SMTP down twice, then up -> retried and sent, still one row")
    void transientFailure_isRetriedThenSent() {
        String eventId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        doThrow(new MailSendException("SMTP down"))
                .doThrow(new MailSendException("SMTP still down"))
                .doNothing()
                .when(sender).send(any());

        send(productId, eventJson(eventId, productId));

        Notification n = awaitStatus(eventId, NotificationStatus.SENT);
        assertThat(n.getAttempts()).isEqualTo(3);
        verify(sender, times(3)).send(any());
        assertThat(deadLetters(productId, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    @DisplayName("PERSISTENT failure: retries exhausted -> dead-letter topic, row left PENDING for inspection")
    void persistentFailure_endsInDeadLetterTopic() {
        String eventId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        doThrow(new MailSendException("SMTP down for good")).when(sender).send(any());

        send(productId, eventJson(eventId, productId));

        List<ConsumerRecord<String, String>> dead = deadLetters(productId, WAIT);
        assertThat(dead).hasSize(1);
        assertThat(header(dead.get(0), KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).contains("MailSendException");
        assertThat(dead.get(0).value()).contains(eventId);   // the original event, as JSON

        Notification n = notifications.findByEventId(eventId).orElseThrow();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.getAttempts()).isEqualTo(TOTAL_ATTEMPTS);
        verify(sender, times(TOTAL_ATTEMPTS)).send(any());
    }

    @Test
    @DisplayName("POISON PILL: malformed JSON goes to the DLT and the partition keeps flowing")
    void malformedJson_goesToDlt_andNextRecordOnSamePartitionIsProcessed() {
        String productId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();

        // Same key -> same partition. If the bad record blocked the partition, the good one behind
        // it would never be processed.
        send(productId, "{this is not json");
        send(productId, eventJson(eventId, productId));

        awaitStatus(eventId, NotificationStatus.SENT);
        List<ConsumerRecord<String, String>> dead = deadLetters(productId, WAIT);
        assertThat(dead).hasSize(1);
        assertThat(dead.get(0).value()).isEqualTo("{this is not json");   // original bytes preserved
        verify(sender, times(1)).send(any());
    }

    @Test
    @DisplayName("INVALID event (no productId): straight to the DLT, no retries, nothing stored")
    void invalidEvent_skipsRetries() {
        String eventId = UUID.randomUUID().toString();
        String key = "invalid-" + UUID.randomUUID();

        send(key, """
                {"eventId":"%s","schemaVersion":1,"productName":"Ghost","stockQuantity":1,"threshold":5,
                 "occurredAt":"2026-09-08T10:00:00Z"}""".formatted(eventId));

        List<ConsumerRecord<String, String>> dead = deadLetters(key, WAIT);
        assertThat(dead).hasSize(1);
        assertThat(header(dead.get(0), KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).contains("InvalidEventException");
        assertThat(notifications.findByEventId(eventId)).isEmpty();
        verify(sender, never()).send(any());
    }

    @Test
    @DisplayName("TOLERANT READER: a v2 event with an extra field is still consumed")
    void newerSchemaWithExtraField_isStillConsumed() {
        String eventId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        doNothing().when(sender).send(any());

        send(productId, """
                {"eventId":"%s","schemaVersion":2,"productId":"%s","productName":"Keyboard",
                 "stockQuantity":4,"threshold":5,"occurredAt":"2026-09-08T10:00:00Z",
                 "warehouse":"Lyon-2","supplierEmail":"orders@supplier.test"}""".formatted(eventId, productId));

        awaitStatus(eventId, NotificationStatus.SENT);
    }

    // ---------------------------------------------------------------------------------------

    private static String eventJson(String eventId, String productId) {
        return """
                {"eventId":"%s","schemaVersion":1,"productId":"%s","productName":"Keyboard",
                 "stockQuantity":4,"threshold":5,"occurredAt":"2026-09-08T10:00:00Z"}"""
                .formatted(eventId, productId);
    }

    private static void send(String key, String value) {
        try {
            producer.send(new ProducerRecord<>(TOPIC, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Notification awaitStatus(String eventId, NotificationStatus status) {
        return await().atMost(WAIT).until(
                () -> notifications.findByEventId(eventId).filter(n -> n.getStatus() == status),
                Optional::isPresent).orElseThrow();
    }

    private double handledCount(String outcome) {
        return meterRegistry.counter("notifications.stock_low.handled", "outcome", outcome).count();
    }

    private static List<ConsumerRecord<String, String>> deadLetters(String key, Duration window) {
        dltReader.subscribe(List.of(DLT));
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline && matching.isEmpty()) {
            dltReader.poll(Duration.ofMillis(250)).forEach(r -> {
                if (key.equals(r.key())) {
                    matching.add(r);
                }
            });
        }
        return matching;
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? "" : new String(h.value(), StandardCharsets.UTF_8);
    }
}

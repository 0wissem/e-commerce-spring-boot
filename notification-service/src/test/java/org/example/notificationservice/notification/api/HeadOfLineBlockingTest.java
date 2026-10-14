package org.example.notificationservice.notification.api;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.notificationservice.AbstractIntegrationTest;
import org.example.notificationservice.notification.domain.INotificationSender;
import org.example.notificationservice.notification.domain.Notification;
import org.example.notificationservice.notification.domain.NotificationStatus;
import org.example.notificationservice.notification.infrastructure.NotificationJpaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * THE REGRESSION TEST for the bug that cost three weeks.
 *
 * On the cluster, with SMTP down, consumption fell to 0.27 events/s — the blocking backoff held the
 * consumer thread for 1s + 2s + 4s per failing record, and Kafka reads a partition strictly in
 * order, so a healthy product's alert waited behind a broken one. Non-blocking retries fix that by
 * parking the failure on a retry topic.
 *
 * The test encodes exactly that: a failing event, then a good event behind it ON THE SAME PARTITION
 * (same key), with the retry delay set to 4s. The good one must be processed in well under that.
 * Put the blocking retries back and this test fails on the clock, not on a side effect.
 *
 * It runs with its own topics and its own consumer group, so it cannot steal partitions from
 * StockLowConsumerIntegrationTest's context, which is alive at the same time.
 */
@TestPropertySource(properties = {
        "spring.kafka.consumer.group-id=hol-test-group",
        "app.kafka.topics.stock-low=stock.low.hol",
        "app.kafka.topics.stock-low-dlt=stock.low.hol.DLT",
        "app.kafka.topics.partitions=1",
        "app.kafka.listener.concurrency=1",
        "app.kafka.retry.initial-interval-ms=4000",
        "app.kafka.retry.attempts=3",
})
class HeadOfLineBlockingTest extends AbstractIntegrationTest {

    private static final String TOPIC = "stock.low.hol";
    private static final Duration RETRY_DELAY = Duration.ofSeconds(4);

    private static KafkaProducer<String, String> producer;

    /**
     * Static block, NOT @BeforeAll: the topic must exist before Spring starts, because a consumer
     * that subscribes to a missing topic only notices it on the next metadata refresh. Creating it
     * on first send passed when this class ran alone and failed in the full suite — a 3-second
     * assertion lost to metadata timing, not to the behaviour under test.
     */
    static {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("could not create " + TOPIC, e);
        }
    }

    @MockitoBean private INotificationSender sender;
    @Autowired private NotificationJpaRepository notifications;
    @Autowired private KafkaListenerEndpointRegistry listeners;

    @BeforeAll
    static void openProducer() {
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @AfterAll
    static void closeProducer() {
        producer.close();
    }

    /**
     * The assertion below is a stopwatch, so the consumer must already own its partition before the
     * clock starts. Four containers (main + two retry topics + DLT) join this group at startup, and
     * the rebalance took longer than the 3s budget when the whole suite ran — the test failed on
     * startup timing while the behaviour under test was fine. Wait for the assignment, then measure.
     */
    @BeforeEach
    void waitUntilAssigned() {
        MessageListenerContainer container = listeners.getListenerContainer("stock-low-listener");
        await().atMost(Duration.ofSeconds(30)).until(() -> container.getAssignedPartitions() != null
                && !container.getAssignedPartitions().isEmpty());
    }

    @Test
    @DisplayName("a failing event does NOT hold up the healthy event behind it on the same partition")
    void failingRecordDoesNotBlockTheNextOne() {
        String key = UUID.randomUUID().toString();     // same key => same partition => strict order
        String failingEventId = UUID.randomUUID().toString();
        String healthyEventId = UUID.randomUUID().toString();

        // Only the first event's email fails; the one behind it would succeed immediately.
        doThrow(new MailSendException("SMTP down for this one"))
                .when(sender).send(argThat(n -> n != null && failingEventId.equals(n.getEventId())));

        long start = System.nanoTime();
        send(key, event(failingEventId, key));
        send(key, event(healthyEventId, key));

        Notification healthy = await().atMost(RETRY_DELAY.minusSeconds(1)).until(
                () -> notifications.findByEventId(healthyEventId)
                        .filter(n -> n.getStatus() == NotificationStatus.SENT),
                Optional::isPresent).orElseThrow();

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed)
                .as("the healthy event must not wait for the failing one's backoff")
                .isLessThan(RETRY_DELAY);
        assertThat(healthy.getAttempts()).isEqualTo(1);

        // ...and the failing one is still in flight on the retry topic, not lost and not blocking.
        assertThat(notifications.findByEventId(failingEventId).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.PENDING);
    }

    private static String event(String eventId, String productId) {
        return """
                {"eventId":"%s","schemaVersion":1,"productId":"%s","productName":"HOL probe",
                 "stockQuantity":4,"threshold":5,"occurredAt":"2026-10-07T10:00:00Z"}"""
                .formatted(eventId, productId);
    }

    private static void send(String key, String value) {
        try {
            producer.send(new ProducerRecord<>(TOPIC, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

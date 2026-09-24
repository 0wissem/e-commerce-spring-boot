package org.example.productservice.product.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.productservice.AbstractIntegrationTest;
import org.example.productservice.product.application.IProductService;
import org.example.productservice.product.application.StockService;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.Product;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end on real infrastructure: real Postgres transaction -> AFTER_COMMIT -> real Kafka broker.
 *
 * The test reads the topic with a plain KafkaConsumer, NOT the application's code, so it checks
 * what actually reached the wire: the key, the headers, the JSON field names.
 *
 * The topic is shared by every test in the suite (other tests decrement stock too), so each test
 * uses a fresh productId and only counts records carrying that key.
 */
class StockLowKafkaIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static KafkaConsumer<String, String> consumer;

    @Autowired private IProductService productService;
    @Autowired private StockService stockService;
    @Autowired private IProductRepository productRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Value("${app.kafka.topics.stock-low}") private String topic;

    private final List<String> createdIds = new ArrayList<>();

    @BeforeAll
    static void openConsumer() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    @AfterAll
    static void closeConsumer() {
        consumer.close();
    }

    @AfterEach
    void tearDown() {
        createdIds.forEach(productRepository::deleteById);
    }

    @Test
    @DisplayName("crossing the threshold puts ONE record on stock.low, keyed by productId, with the JSON contract")
    void crossingThreshold_publishesOneRecord() throws Exception {
        String id = product(6);   // threshold is 5

        productService.decrementStock(id, 2);   // 6 -> 4

        List<ConsumerRecord<String, String>> records = recordsFor(id, Duration.ofSeconds(10), 1);
        assertThat(records).hasSize(1);

        ConsumerRecord<String, String> record = records.get(0);
        assertThat(record.key()).isEqualTo(id);
        // No __TypeId__ header: the consumer must not depend on our Java package names.
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();

        JsonNode json = JSON.readTree(record.value());
        assertThat(json.get("productId").asString()).isEqualTo(id);
        assertThat(json.get("stockQuantity").asInt()).isEqualTo(4);
        assertThat(json.get("threshold").asInt()).isEqualTo(5);
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("eventId").asString()).isNotBlank();
        // An ISO-8601 string, not an epoch number — readable by any consumer in any language.
        assertThat(Instant.parse(json.get("occurredAt").asString())).isNotNull();
    }

    @Test
    @DisplayName("a decrement that stays above the threshold publishes nothing")
    void aboveThreshold_publishesNothing() {
        String id = product(20);

        productService.decrementStock(id, 3);   // 20 -> 17

        assertThat(recordsFor(id, Duration.ofSeconds(3), 1)).isEmpty();
    }

    @Test
    @DisplayName("ROLLED BACK transaction: the event was raised, but nothing reaches Kafka")
    void rolledBackTransaction_publishesNothing() {
        String id = product(6);

        transactionTemplate.executeWithoutResult(status -> {
            productService.decrementStock(id, 2);   // joins this transaction; the event IS raised
            status.setRollbackOnly();               // ...and then the whole unit is undone
        });

        assertThat(productRepository.findById(id).orElseThrow().getStockQuantity()).isEqualTo(6);
        assertThat(recordsFor(id, Duration.ofSeconds(3), 1))
                .as("AFTER_COMMIT must never fire for a rollback")
                .isEmpty();
    }

    @Test
    @DisplayName("10 threads race through the threshold with optimistic-lock retries: exactly ONE alert")
    void concurrentDecrements_withRetries_publishExactlyOneAlert() throws Exception {
        String id = product(10);
        int threads = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                startGun.await();
                return stockService.decrementStock(id, 1);
            });
        }
        startGun.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(productRepository.findById(id).orElseThrow().getStockQuantity()).isZero();
        // Many attempts computed "6 -> 5" and then lost the version race at commit. Had the
        // event been sent inside the transaction, every one of those losers would have alerted.
        assertThat(recordsFor(id, Duration.ofSeconds(8), 2))
                .as("the 6 -> 5 crossing committed exactly once")
                .hasSize(1);
    }

    // ---------------------------------------------------------------------------------------

    private String product(int stock) {
        String id = UUID.randomUUID().toString();
        productRepository.save(new Product(id, "Kafka test item", new BigDecimal("10.00"), stock));
        createdIds.add(id);
        return id;
    }

    /**
     * Polls until {@code stopAfter} matching records arrived or the window closed. Asking for one
     * more record than expected makes "exactly one" a real check: the poll keeps listening for
     * the whole window instead of stopping at the first hit.
     */
    private List<ConsumerRecord<String, String>> recordsFor(String productId, Duration window, int stopAfter) {
        consumer.subscribe(List.of(topic));
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline && matching.size() < stopAfter) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                if (productId.equals(record.key())) {
                    matching.add(record);
                }
            }
        }
        return matching;
    }
}

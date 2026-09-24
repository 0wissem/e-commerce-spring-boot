package org.example.notificationservice;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.util.List;
import java.util.Map;

/**
 * Base class for integration tests: ONE Postgres and ONE Kafka broker for the whole run
 * (singleton containers), stopped by Ryuk at JVM exit.
 *
 * The stock.low topic is created here because product-service owns it in real life; this service
 * only declares its own dead-letter topic.
 *
 * Retries are shrunk to 100ms/200ms so the retry and dead-letter tests take a second, not seven.
 */
@SpringBootTest(properties = {
        "app.kafka.retry.initial-interval-ms=100",
        "app.kafka.retry.max-retries=2",
        "notification.stock-low.recipient=stock-team@test.local"
})
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        POSTGRES.start();
        KAFKA.start();
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic("stock.low", 3, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the stock.low test topic", e);
        }
    }

    @DynamicPropertySource
    static void containerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}

package org.example.productservice.product.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.example.productservice.product.domain.LowStockPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class StockMessagingConfig {

    /** The domain policy stays framework-free; Spring only supplies the configured number. */
    @Bean
    public LowStockPolicy lowStockPolicy(@Value("${stock.low-threshold}") int threshold) {
        return new LowStockPolicy(threshold);
    }

    /**
     * The topic is declared by its OWNER (the producer), in code, so it exists with the right
     * shape in every environment. The broker has auto-create disabled on purpose: a typo in a
     * topic name should fail loudly, not silently create a second topic with default settings.
     *
     * 3 partitions = up to 3 consumers in one group reading in parallel. Partitions can be added
     * later but never removed, and adding them re-maps keys to partitions — so pick it up front.
     */
    @Bean
    public NewTopic stockLowTopic(@Value("${app.kafka.topics.stock-low}") String name,
                                  @Value("${app.kafka.topics.partitions:3}") int partitions,
                                  @Value("${app.kafka.topics.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}

package org.example.productservice.product.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final IProductRepository productRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductEventConsumer(IProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @KafkaListener(topics = "product.events", groupId = "product-service-group")
    public void consume(String message) {
        try {
            ProductEventPayload payload = objectMapper.readValue(message, ProductEventPayload.class);

            if (!"monolith".equals(payload.source())) {
                return;
            }

            log.info("Received product event: {} - {}", payload.eventType(), payload.productId());

            switch (payload.eventType()) {
                case "PRODUCT_CREATED" -> handleCreated(payload);
                case "PRODUCT_UPDATED" -> handleUpdated(payload);
                case "PRODUCT_DELETED" -> handleDeleted(payload);
                default -> log.warn("Unknown event type: {}", payload.eventType());
            }
        } catch (Exception e) {
            log.error("Failed to process product event: {}", message, e);
        }
    }

    private void handleCreated(ProductEventPayload payload) {
        // idempotent: upsert by monolith product ID
        productRepository.findById(payload.productId()).ifPresentOrElse(
                existing -> {
                    existing.setName(payload.name());
                    existing.setPrice(payload.price());
                    existing.setStockQuantity(payload.stockQuantity());
                    productRepository.save(existing);
                },
                () -> {
                    Product product = new Product(payload.productId(), payload.name(), payload.price(), payload.stockQuantity());
                    productRepository.save(product);
                }
        );
    }

    private void handleUpdated(ProductEventPayload payload) {
        productRepository.findById(payload.productId()).ifPresentOrElse(
                product -> {
                    product.setName(payload.name());
                    product.setPrice(payload.price());
                    product.setStockQuantity(payload.stockQuantity());
                    productRepository.save(product);
                },
                // out-of-order: event arrived before CREATED — treat as create
                () -> handleCreated(payload)
        );
    }

    private void handleDeleted(ProductEventPayload payload) {
        productRepository.findById(payload.productId()).ifPresent(product -> {
            product.setDeletedAt(LocalDateTime.now());
            productRepository.save(product);
        });
    }
}
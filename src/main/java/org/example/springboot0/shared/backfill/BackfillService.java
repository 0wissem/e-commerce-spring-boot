package org.example.springboot0.shared.backfill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springboot0.product.domain.IProductRepository;
import org.example.springboot0.product.domain.Product;
import org.example.springboot0.shared.outbox.domain.IOutboxEventRepository;
import org.example.springboot0.shared.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);
    private static final int PAGE_SIZE = 500;

    private final IProductRepository productRepository;
    private final IOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public BackfillService(IProductRepository productRepository,
                           IOutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional
    public void runBackfill() {
        int page = 0;
        int total = 0;

        Page<Product> batch;
        do {
            batch = productRepository.findAll(PageRequest.of(page, PAGE_SIZE));
            for (Product product : batch.getContent()) {
                outboxEventRepository.save(buildOutboxEvent(product));
                total++;
            }
            log.info("Backfill: queued page {} ({} products so far)", page, total);
            page++;
        } while (batch.hasNext());

        log.info("Backfill complete: {} PRODUCT_CREATED events queued", total);
    }

    private OutboxEvent buildOutboxEvent(Product product) {
        try {
            java.util.List<String> categoryNames = product.getCategories().stream()
                    .map(org.example.springboot0.category.domain.Category::getName)
                    .toList();
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", "PRODUCT_CREATED",
                    "source", "monolith",
                    "productId", product.getId(),
                    "name", product.getName(),
                    "price", product.getPrice(),
                    "stockQuantity", product.getStockQuantity(),
                    "categoryNames", categoryNames
            ));
            return new OutboxEvent(UUID.randomUUID().toString(), "PRODUCT_CREATED", "monolith", payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize backfill event for product " + product.getId(), e);
        }
    }
}

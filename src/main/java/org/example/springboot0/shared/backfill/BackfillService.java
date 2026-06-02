package org.example.springboot0.shared.backfill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springboot0.order.domain.IOrderRepository;
import org.example.springboot0.product.domain.IProductRepository;
import org.example.springboot0.product.domain.Product;
import org.example.springboot0.shared.event.CategoryDto;
import org.example.springboot0.shared.outbox.domain.IOutboxEventRepository;
import org.example.springboot0.shared.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);
    private static final int PAGE_SIZE = 500;

    private final IProductRepository productRepository;
    private final IOutboxEventRepository outboxEventRepository;
    private final IOrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final OrderBackfillProcessor orderBackfillProcessor;

    public BackfillService(IProductRepository productRepository,
                           IOutboxEventRepository outboxEventRepository,
                           IOrderRepository orderRepository,
                           ObjectMapper objectMapper,
                           OrderBackfillProcessor orderBackfillProcessor) {
        this.productRepository = productRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.orderBackfillProcessor = orderBackfillProcessor;
    }

    @Async
    @Transactional
    public void runProductBackfill() {
        int page = 0;
        int total = 0;

        Page<Product> batch;
        do {
            batch = productRepository.findAll(PageRequest.of(page, PAGE_SIZE));
            for (Product product : batch.getContent()) {
                outboxEventRepository.save(buildOutboxEvent(product));
                total++;
            }
            log.info("Product backfill: queued page {} ({} products so far)", page, total);
            page++;
        } while (batch.hasNext());

        log.info("Product backfill complete: {} PRODUCT_CREATED events queued", total);
    }

    @Async
    public void runOrderBackfill() {
        int page = 0;
        int[] totals = {0, 0}; // [updated, skipped]
        boolean hasNext;
        do {
            hasNext = orderBackfillProcessor.processPage(page, PAGE_SIZE, totals);
            log.info("Order backfill: page {} done ({} updated, {} skipped so far)", page, totals[0], totals[1]);
            page++;
        } while (hasNext);
        log.info("Order backfill complete: {} snapshots updated, {} skipped (product deleted)", totals[0], totals[1]);
    }

    private OutboxEvent buildOutboxEvent(Product product) {
        try {
            List<CategoryDto> categories = product.getCategories().stream()
                    .map(c -> new CategoryDto(c.getId(), c.getName()))
                    .toList();
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", "PRODUCT_CREATED",
                    "source", "monolith",
                    "productId", product.getId(),
                    "name", product.getName(),
                    "price", product.getPrice(),
                    "stockQuantity", product.getStockQuantity(),
                    "categories", categories
            ));
            return new OutboxEvent(UUID.randomUUID().toString(), "PRODUCT_CREATED", "monolith", payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize backfill event for product " + product.getId(), e);
        }
    }
}

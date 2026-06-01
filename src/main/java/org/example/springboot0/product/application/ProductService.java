package org.example.springboot0.product.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springboot0.category.domain.ICategoryRepository;
import org.example.springboot0.product.domain.IStockEventPublisher;
import org.example.springboot0.product.domain.StockUpdatedEvent;
import org.example.springboot0.product.application.dto.ProductRequest;
import org.example.springboot0.product.application.dto.ProductResponse;
import org.example.springboot0.product.application.dto.ProductSearchRequest;
import org.example.springboot0.product.domain.IProductRepository;
import org.example.springboot0.product.domain.Product;
import org.example.springboot0.shared.exception.ResourceNotFoundException;
import org.example.springboot0.shared.outbox.domain.IOutboxEventRepository;
import org.example.springboot0.shared.outbox.domain.OutboxEvent;
import org.example.springboot0.shared.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.example.springboot0.shared.event.CategoryDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ProductService implements IProductService {

    private final IProductRepository productRepository;
    private final ICategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final IStockEventPublisher stockEventPublisher;
    private final IOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ProductService(IProductRepository productRepository,
                          ICategoryRepository categoryRepository,
                          ProductMapper productMapper,
                          IStockEventPublisher stockEventPublisher,
                          IOutboxEventRepository outboxEventRepository,
                          ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
        this.stockEventPublisher = stockEventPublisher;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ProductResponse> getAll() {
        return productRepository.findAll().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    public PageResponse<ProductResponse> getAll(int page, int size) {
        return PageResponse.from(
                productRepository.findAll(PageRequest.of(page, size))
                        .map(productMapper::toResponse)
        );
    }

    @Override
    public ProductResponse getById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toResponse(product);
    }

    @Override
    public ProductResponse getByName(String name) {
        Product product = productRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with name: " + name));
        return productMapper.toResponse(product);
    }

    @Override
    public ProductResponse create(ProductRequest request) {
        Product product = productMapper.toDomain(request);
        if (request.categoryIds() != null && !request.categoryIds().isEmpty()) {
            product.setCategories(categoryRepository.findAllByIds(request.categoryIds()));
        }
        Product saved = productRepository.save(product);
        saveOutboxEvent("PRODUCT_CREATED", saved);
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse update(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setName(request.name());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        if (request.categoryIds() != null) {
            product.setCategories(categoryRepository.findAllByIds(request.categoryIds()));
        }
        Product saved = productRepository.save(product);
        stockEventPublisher.publish(new StockUpdatedEvent(saved.getId(), saved.getName(), saved.getStockQuantity()));
        saveOutboxEvent("PRODUCT_UPDATED", saved);
        return productMapper.toResponse(saved);
    }

    @Override
    public void delete(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setDeletedAt(java.time.LocalDateTime.now());
        productRepository.save(product);
        saveOutboxEvent("PRODUCT_DELETED", product);
    }

    private void saveOutboxEvent(String eventType, Product product) {
        try {
            List<CategoryDto> categories = product.getCategories().stream()
                    .map(c -> new CategoryDto(c.getId(), c.getName()))
                    .toList();
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", eventType,
                    "source", "monolith",
                    "productId", product.getId(),
                    "name", product.getName(),
                    "price", product.getPrice(),
                    "stockQuantity", product.getStockQuantity(),
                    "categories", categories
            ));
            outboxEventRepository.save(new OutboxEvent(UUID.randomUUID().toString(), eventType, "monolith", payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }

    @Override
    public PageResponse<ProductResponse> search(ProductSearchRequest request) {
        return PageResponse.from(
                productRepository.search(
                        request.query(),
                        request.minPrice(),
                        request.maxPrice(),
                        request.categoryId(),
                        request.inStock(),
                        PageRequest.of(request.page(), request.size())
                ).map(productMapper::toResponse)
        );
    }
}
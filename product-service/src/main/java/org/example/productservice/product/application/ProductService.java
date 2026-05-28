package org.example.productservice.product.application;

import org.example.productservice.category.domain.ICategoryRepository;
import org.example.productservice.product.domain.IStockEventPublisher;
import org.example.productservice.product.domain.StockUpdatedEvent;
import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.application.dto.ProductSearchRequest;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.Product;
import org.example.productservice.shared.exception.ResourceNotFoundException;
import org.example.productservice.shared.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductService implements IProductService {

    private final IProductRepository productRepository;
    private final ICategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final IStockEventPublisher stockEventPublisher;

    public ProductService(IProductRepository productRepository,
                          ICategoryRepository categoryRepository,
                          ProductMapper productMapper,
                          IStockEventPublisher stockEventPublisher) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
        this.stockEventPublisher = stockEventPublisher;
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
        return productMapper.toResponse(productRepository.save(product));
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
        ProductResponse response = productMapper.toResponse(productRepository.save(product));
        stockEventPublisher.publish(new StockUpdatedEvent(product.getId(), product.getName(), product.getStockQuantity()));
        return response;
    }

    @Override
    public void delete(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setDeletedAt(java.time.LocalDateTime.now());
        productRepository.save(product);
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
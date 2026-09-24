package org.example.productservice.product.application;

import org.example.productservice.category.domain.ICategoryRepository;
import org.example.productservice.product.domain.IStockEventPublisher;
import org.example.productservice.product.domain.LowStockPolicy;
import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.application.dto.ProductSearchRequest;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.Product;
import org.example.productservice.shared.exception.ResourceNotFoundException;
import org.example.productservice.shared.response.PageResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    private final LowStockPolicy lowStockPolicy;

    public ProductService(IProductRepository productRepository,
                          ICategoryRepository categoryRepository,
                          ProductMapper productMapper,
                          IStockEventPublisher stockEventPublisher,
                          LowStockPolicy lowStockPolicy) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
        this.stockEventPublisher = stockEventPublisher;
        this.lowStockPolicy = lowStockPolicy;
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
    @Cacheable(value = "products", key = "#id")
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
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse update(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        int stockBefore = product.getStockQuantity();
        product.setName(request.name());
        product.setPriceAmount(request.price());
        product.setStockQuantity(request.stockQuantity());
        productMapper.applyOptionalFields(product, request);
        if (request.categoryIds() != null) {
            product.setCategories(categoryRepository.findAllByIds(request.categoryIds()));
        }
        ProductResponse response = productMapper.toResponse(productRepository.save(product));
        publishIfLow(product, stockBefore);   // an admin editing stock down can cross it too
        return response;
    }

    /**
     * Decrements stock inside ONE transaction.
     *
     * The optimistic-lock conflict surfaces on flush, not on save(): Hibernate batches the
     * UPDATE until the transaction commits, so the exception is thrown as this method
     * returns. That is precisely why the retry cannot live in this class — by the time the
     * failure is visible, this transaction is already doomed and must be rolled back before
     * anything is retried. {@link StockService} wraps it from outside.
     */
    @Override
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse decrementStock(String id, int quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        int stockBefore = product.getStockQuantity();

        product.decrementStock(quantity);   // the invariant is enforced by the entity

        ProductResponse response = productMapper.toResponse(productRepository.save(product));
        publishIfLow(product, stockBefore);
        return response;
    }

    /**
     * Same operation, PESSIMISTIC strategy — the deliberate counterpart to
     * {@link #decrementStock}.
     *
     * `findByIdForUpdate` issues SELECT ... FOR UPDATE, so the row is locked before it is
     * read. A second transaction blocks right there until this one commits, then reads the
     * updated value. No conflict can occur, so there is nothing to retry — which is why no
     * StockService wrapper is needed for this path.
     *
     * When to prefer it: HIGH contention on a single row (a flash sale on one item), where
     * optimistic retries would thrash. When to avoid it: everything else — it serialises
     * callers and holds a DB lock for the length of the transaction.
     */
    @Override
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse decrementStockPessimistic(String id, int quantity) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        int stockBefore = product.getStockQuantity();

        product.decrementStock(quantity);

        ProductResponse response = productMapper.toResponse(productRepository.save(product));
        publishIfLow(product, stockBefore);
        return response;
    }

    /** Puts units back — the compensating action for a failed order. */
    @Override
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse incrementStock(String id, int quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        product.incrementStock(quantity);

        // Stock only goes UP here, so it can never cross the low-stock threshold downward.
        return productMapper.toResponse(productRepository.save(product));
    }

    /**
     * Hands a low-stock event to the port — it is NOT on Kafka yet when this returns.
     *
     * We are inside the transaction here, and it can still fail: with optimistic locking the
     * version conflict only surfaces at flush, AFTER this line. The adapter therefore holds the
     * event until commit (see AfterCommitStockEventPublisher). Sending right here would alert on
     * decrements that StockService is about to roll back and retry — phantom alerts.
     */
    private void publishIfLow(Product product, int stockBefore) {
        lowStockPolicy.evaluate(product, stockBefore).ifPresent(stockEventPublisher::publish);
    }

    @Override
    @CacheEvict(value = "products", key = "#id")
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
                        request.brand(),
                        request.categoryId(),
                        request.inStock(),
                        PageRequest.of(request.page(), request.size())
                ).map(productMapper::toResponse)
        );
    }
}
package org.example.productservice;

import org.example.productservice.product.application.IProductService;
import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves @Cacheable / @CacheEvict on ProductService. The repository is mocked so we can
 * count actual DB reads: a cache hit must NOT reach the repository. (@SpringBootTest so the
 * caching proxy is active; the shared Testcontainers Postgres just lets the context start.)
 */
class ProductCacheTest extends AbstractIntegrationTest {

    @Autowired
    private IProductService productService;
    @Autowired
    private CacheManager cacheManager;
    @MockitoBean
    private IProductRepository productRepository;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache("products").clear();
    }

    private Product product() {
        return new Product("p1", "Keyboard", 100.0, 5);
    }

    @Test
    @DisplayName("getById is cached: two calls hit the DB only once")
    void cacheHit() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(product()));

        productService.getById("p1");
        productService.getById("p1"); // served from cache

        verify(productRepository, times(1)).findById("p1");
    }

    @Test
    @DisplayName("update evicts the entry: a later getById re-reads from the DB")
    void evictOnUpdate() {
        when(productRepository.findById("p1")).thenReturn(Optional.of(product()));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.getById("p1"); // caches
        clearInvocations(productRepository);

        productService.getById("p1"); // cache HIT → no DB read
        verify(productRepository, never()).findById("p1");

        productService.update("p1", new ProductRequest("New name", 20.0, 5, null)); // @CacheEvict
        clearInvocations(productRepository);

        productService.getById("p1"); // cache MISS → DB read again
        verify(productRepository, times(1)).findById("p1");
    }
}

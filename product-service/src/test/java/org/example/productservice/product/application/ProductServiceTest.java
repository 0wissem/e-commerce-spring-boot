package org.example.productservice.product.application;

import org.example.productservice.category.domain.ICategoryRepository;
import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.IStockEventPublisher;
import org.example.productservice.product.domain.Product;
import org.example.productservice.product.domain.StockUpdatedEvent;
import org.example.productservice.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UNIT test for ProductService: the service in isolation, all collaborators mocked.
 * No Spring, no database — pure, fast, deterministic. This is the base of the test pyramid.
 *
 * @ExtendWith(MockitoExtension.class) wires Mockito into JUnit 5 (creates the @Mock objects,
 * verifies no unused stubs, etc.).
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    // Collaborators that DO I/O (DB, events) → mock them, we control their behaviour.
    @Mock
    private IProductRepository productRepository;
    @Mock
    private ICategoryRepository categoryRepository;
    @Mock
    private IStockEventPublisher stockEventPublisher;

    // The mapper is PURE logic (no I/O) → use the real one. Rule of thumb: don't mock
    // what you can cheaply build for real; mock only the things at the edges (I/O).
    private final ProductMapper productMapper = new ProductMapper();

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, categoryRepository, productMapper, stockEventPublisher);
    }

    @Test
    @DisplayName("getById: returns the mapped product when it exists")
    void getById_returnsMappedProduct_whenFound() {
        // Arrange — set up the mock to return a product
        Product product = new Product("p1", "Keyboard", 100.0, 5);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        // Act
        ProductResponse response = service.getById("p1");

        // Assert
        assertThat(response.id()).isEqualTo("p1");
        assertThat(response.name()).isEqualTo("Keyboard");
        assertThat(response.price()).isEqualTo(100.0);
        // finalPrice = price * 1.19 (business rule). Compare doubles with a tolerance.
        assertThat(response.finalPrice()).isCloseTo(119.0, within(0.0001));
    }

    @Test
    @DisplayName("getById: throws ResourceNotFoundException when missing")
    void getById_throwsNotFound_whenMissing() {
        when(productRepository.findById("nope")).thenReturn(Optional.empty());

        // assertThatThrownBy is AssertJ's way to assert on a thrown exception
        assertThatThrownBy(() -> service.getById("nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    @DisplayName("create: saves the product and returns the response")
    void create_savesProduct_andReturnsResponse() {
        ProductRequest request = new ProductRequest("Mouse", 50.0, 10, null);
        // thenAnswer: make save() return its own argument (mimics the DB returning the saved row)
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = service.create(request);

        assertThat(response.name()).isEqualTo("Mouse");
        verify(productRepository).save(any(Product.class));
        // no categoryIds in the request → the category repo must never be touched
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("update: applies changes and publishes a stock event")
    void update_savesChanges_andPublishesStockEvent() {
        Product existing = new Product("p1", "Old", 10.0, 1);
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest("New", 20.0, 7, null);
        ProductResponse response = service.update("p1", request);

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.stockQuantity()).isEqualTo(7);
        // the important side effect: a stock event is published on update
        verify(stockEventPublisher).publish(any(StockUpdatedEvent.class));
    }

    @Test
    @DisplayName("delete: is a SOFT delete (stamps deletedAt, saves, never hard-deletes)")
    void delete_isSoftDelete() {
        Product product = new Product("p1", "Keyboard", 100.0, 5);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        service.delete("p1");

        assertThat(product.getDeletedAt()).isNotNull();        // soft-delete timestamp set
        verify(productRepository).save(product);                // persisted
        verify(productRepository, never()).deleteById(anyString()); // never a hard delete
    }
}

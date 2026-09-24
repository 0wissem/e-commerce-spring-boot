package org.example.productservice.product.application;

import org.example.productservice.category.domain.ICategoryRepository;
import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.domain.IProductRepository;
import org.example.productservice.product.domain.IStockEventPublisher;
import org.example.productservice.product.domain.Product;
import org.example.productservice.product.domain.LowStockPolicy;
import org.example.productservice.product.domain.StockLowEvent;
import org.example.productservice.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    // Pure domain rule, no I/O → real instance too. Threshold 5 for every test here.
    private final LowStockPolicy lowStockPolicy = new LowStockPolicy(5);

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, categoryRepository, productMapper, stockEventPublisher, lowStockPolicy);
    }

    @Test
    @DisplayName("getById: returns the mapped product when it exists")
    void getById_returnsMappedProduct_whenFound() {
        // Arrange — set up the mock to return a product
        Product product = new Product("p1", "Keyboard", new BigDecimal("100.00"), 5);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        // Act
        ProductResponse response = service.getById("p1");

        // Assert
        assertThat(response.id()).isEqualTo("p1");
        assertThat(response.name()).isEqualTo("Keyboard");
        // isEqualByComparingTo, not isEqualTo: BigDecimal.equals() also compares scale,
        // so 100.00 and 100.0 would be "different". compareTo compares value only.
        assertThat(response.price()).isEqualByComparingTo("100.00");
        // finalPrice = price * 1.19. With BigDecimal the result is EXACT — the tolerance
        // this assertion used to need (isCloseTo/within) was a symptom of the double bug.
        assertThat(response.finalPrice()).isEqualByComparingTo("119.00");
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
        ProductRequest request = new ProductRequest("Mouse", null, null, new BigDecimal("50.00"), null, 10, null);
        // thenAnswer: make save() return its own argument (mimics the DB returning the saved row)
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = service.create(request);

        assertThat(response.name()).isEqualTo("Mouse");
        verify(productRepository).save(any(Product.class));
        // no categoryIds in the request → the category repo must never be touched
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("update: applies changes; raising stock publishes nothing")
    void update_savesChanges_withoutEvent_whenStockRises() {
        Product existing = new Product("p1", "Old", new BigDecimal("10.00"), 1);
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest("New", null, null, new BigDecimal("20.00"), null, 7, null);
        ProductResponse response = service.update("p1", request);

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.stockQuantity()).isEqualTo(7);
        verifyNoInteractions(stockEventPublisher);
    }

    @Test
    @DisplayName("update: an admin cutting stock across the threshold publishes a low-stock event")
    void update_publishesStockLow_whenAdminCutsStockAcrossThreshold() {
        Product existing = new Product("p1", "Keyboard", new BigDecimal("10.00"), 20);
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update("p1", new ProductRequest("Keyboard", null, null, new BigDecimal("10.00"), null, 2, null));

        verify(stockEventPublisher).publish(any(StockLowEvent.class));
    }

    @Test
    @DisplayName("decrementStock: crossing the threshold publishes ONE event with the full contract")
    void decrementStock_publishesStockLow_whenCrossingThreshold() {
        Product product = new Product("p1", "Keyboard", new BigDecimal("10.00"), 6);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.decrementStock("p1", 2);   // 6 -> 4, threshold 5

        ArgumentCaptor<StockLowEvent> captor = ArgumentCaptor.forClass(StockLowEvent.class);
        verify(stockEventPublisher).publish(captor.capture());
        StockLowEvent event = captor.getValue();
        assertThat(event.productId()).isEqualTo("p1");
        assertThat(event.productName()).isEqualTo("Keyboard");
        assertThat(event.stockQuantity()).isEqualTo(4);
        assertThat(event.threshold()).isEqualTo(5);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.schemaVersion()).isEqualTo(StockLowEvent.SCHEMA_VERSION);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("decrementStock: selling an item that is ALREADY low publishes nothing (no alert spam)")
    void decrementStock_publishesNothing_whenAlreadyBelowThreshold() {
        Product product = new Product("p1", "Keyboard", new BigDecimal("10.00"), 4);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.decrementStock("p1", 1);   // 4 -> 3: low, but it was already low

        verifyNoInteractions(stockEventPublisher);
    }

    @Test
    @DisplayName("decrementStock: insufficient stock throws before anything is published")
    void decrementStock_publishesNothing_whenStockInsufficient() {
        Product product = new Product("p1", "Keyboard", new BigDecimal("10.00"), 6);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.decrementStock("p1", 50))
                .isInstanceOf(org.example.productservice.product.domain.InsufficientStockException.class);
        verifyNoInteractions(stockEventPublisher);
    }

    @Test
    @DisplayName("incrementStock: restocking never publishes a low-stock event")
    void incrementStock_neverPublishes() {
        Product product = new Product("p1", "Keyboard", new BigDecimal("10.00"), 0);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.incrementStock("p1", 3);

        verifyNoInteractions(stockEventPublisher);
    }

    @Test
    @DisplayName("delete: is a SOFT delete (stamps deletedAt, saves, never hard-deletes)")
    void delete_isSoftDelete() {
        Product product = new Product("p1", "Keyboard", new BigDecimal("100.00"), 5);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        service.delete("p1");

        assertThat(product.getDeletedAt()).isNotNull();        // soft-delete timestamp set
        verify(productRepository).save(product);                // persisted
        verify(productRepository, never()).deleteById(anyString()); // never a hard delete
    }
}

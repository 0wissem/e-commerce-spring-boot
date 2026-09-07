package org.example.productservice.product.api;

import org.example.productservice.product.application.IProductService;
import org.example.productservice.product.application.StockService;
import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.application.dto.ProductSearchRequest;
import org.example.productservice.product.application.dto.StockDecrementRequest;
import org.example.productservice.shared.response.ApiResponse;
import org.example.productservice.shared.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;


@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final IProductService productService;
    private final StockService stockService;

    public ProductController(IProductService productService, StockService stockService) {
        this.productService = productService;
        this.stockService = stockService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getAll(page, size)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        ProductSearchRequest request =
                new ProductSearchRequest(query, minPrice, maxPrice, brand, categoryId, inStock, page, size);
        return ResponseEntity.ok(ApiResponse.ok(productService.search(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created", productService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(@PathVariable String id,
                                                               @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Product updated", productService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted", null));
    }

    /**
     * Reserves stock for an order. Goes through StockService, which retries on an
     * optimistic-lock conflict — the controller must never see one.
     *
     * POST, not PUT: it is neither idempotent nor a full replacement. Calling it twice
     * removes twice the stock.
     */
    @PostMapping("/{id}/stock/decrement")
    public ResponseEntity<ApiResponse<ProductResponse>> decrementStock(
            @PathVariable String id,
            @Valid @RequestBody StockDecrementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Stock decremented", stockService.decrementStock(id, request.quantity())));
    }

    /** Compensating action: returns reserved units after a failed order. */
    @PostMapping("/{id}/stock/increment")
    public ResponseEntity<ApiResponse<ProductResponse>> incrementStock(
            @PathVariable String id,
            @Valid @RequestBody StockDecrementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Stock incremented", stockService.incrementStock(id, request.quantity())));
    }
}
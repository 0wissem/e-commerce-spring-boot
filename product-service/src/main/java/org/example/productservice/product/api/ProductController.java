package org.example.productservice.product.api;

import org.example.productservice.product.application.IProductService;
import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.application.dto.ProductSearchRequest;
import org.example.productservice.shared.response.ApiResponse;
import org.example.productservice.shared.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final IProductService productService;

    public ProductController(IProductService productService) {
        this.productService = productService;
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
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        ProductSearchRequest request = new ProductSearchRequest(query, minPrice, maxPrice, categoryId, inStock, page, size);
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
}
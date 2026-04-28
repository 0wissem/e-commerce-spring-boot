package org.example.springboot0.product.application;

import org.example.springboot0.product.application.dto.ProductRequest;
import org.example.springboot0.product.application.dto.ProductResponse;
import org.example.springboot0.product.domain.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getFinalPrice(),
                product.getStockQuantity()
        );
    }

    public Product toDomain(ProductRequest request) {
        return new Product(null, request.name(), request.price(), request.stockQuantity());
    }
}
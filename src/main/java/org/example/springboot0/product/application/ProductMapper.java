package org.example.springboot0.product.application;

import org.example.springboot0.product.application.dto.ProductRequest;
import org.example.springboot0.product.application.dto.ProductResponse;
import org.example.springboot0.product.domain.Product;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        Set<ProductResponse.CategoryInfo> categories = product.getCategories().stream()
                .map(c -> new ProductResponse.CategoryInfo(c.getId(), c.getName()))
                .collect(Collectors.toSet());
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getFinalPrice(),
                product.getStockQuantity(),
                categories
        );
    }

    public Product toDomain(ProductRequest request) {
        return new Product(null, request.name(), request.price(), request.stockQuantity());
    }
}
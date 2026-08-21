package org.example.productservice.product.application;

import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.domain.Product;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        Set<ProductResponse.CategoryInfo> categories = product.getCategories().stream()
                .map(c -> new ProductResponse.CategoryInfo(c.getId(), c.getName()))
                .collect(Collectors.toSet());
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getBrand(),
                product.getDescription(),
                product.getPriceAmount(),
                product.getFinalPrice(),
                product.getCurrency(),
                product.getStockQuantity(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                categories
        );
    }

    public Product toDomain(ProductRequest request) {
        Product product = new Product(
                UUID.randomUUID().toString(),
                request.name(),
                request.price(),
                request.stockQuantity()
        );
        applyOptionalFields(product, request);
        return product;
    }

    /** Shared by create and update: only overwrite what the caller actually sent. */
    public void applyOptionalFields(Product product, ProductRequest request) {
        if (request.currency() != null)    product.setCurrency(request.currency());
        if (request.brand() != null)       product.setBrand(request.brand());
        if (request.description() != null) product.setDescription(request.description());
    }
}
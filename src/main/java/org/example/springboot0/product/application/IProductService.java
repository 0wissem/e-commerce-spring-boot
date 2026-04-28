package org.example.springboot0.product.application;

import org.example.springboot0.product.application.dto.ProductRequest;
import org.example.springboot0.product.application.dto.ProductResponse;

import java.util.List;

public interface IProductService {
    List<ProductResponse> getAll();
    ProductResponse getById(String id);
    ProductResponse getByName(String name);
    ProductResponse create(ProductRequest request);
    ProductResponse update(String id, ProductRequest request);
    void delete(String id);
}
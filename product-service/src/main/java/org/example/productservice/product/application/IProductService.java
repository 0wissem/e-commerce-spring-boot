package org.example.productservice.product.application;

import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.application.dto.ProductSearchRequest;
import org.example.productservice.shared.response.PageResponse;

import java.util.List;

public interface IProductService {
    List<ProductResponse> getAll();
    PageResponse<ProductResponse> getAll(int page, int size);
    ProductResponse getById(String id);
    ProductResponse getByName(String name);
    ProductResponse create(ProductRequest request);
    ProductResponse update(String id, ProductRequest request);
    void delete(String id);
    PageResponse<ProductResponse> search(ProductSearchRequest request);
}
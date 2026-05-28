package org.example.productservice.category.application;

import org.example.productservice.category.application.dto.CategoryRequest;
import org.example.productservice.category.application.dto.CategoryResponse;

import java.util.List;

public interface ICategoryService {
    List<CategoryResponse> getAll();
    CategoryResponse getById(String id);
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(String id, CategoryRequest request);
    void delete(String id);
}

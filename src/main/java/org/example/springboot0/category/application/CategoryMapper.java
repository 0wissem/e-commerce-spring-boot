package org.example.springboot0.category.application;

import org.example.springboot0.category.application.dto.CategoryRequest;
import org.example.springboot0.category.application.dto.CategoryResponse;
import org.example.springboot0.category.domain.Category;

import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }

    public Category toDomain(CategoryRequest request) {
        return new Category(null, request.name(), request.description());
    }
}

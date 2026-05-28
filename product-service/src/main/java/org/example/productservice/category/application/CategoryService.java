package org.example.productservice.category.application;

import org.example.productservice.category.application.dto.CategoryRequest;
import org.example.productservice.category.application.dto.CategoryResponse;
import org.example.productservice.category.domain.Category;
import org.example.productservice.category.domain.ICategoryRepository;
import org.springframework.stereotype.Service;

import org.example.productservice.shared.exception.ResourceNotFoundException;

import java.util.List;

@Service
public class CategoryService implements ICategoryService {

    private final ICategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService (ICategoryRepository repository, CategoryMapper mapper) {
        this.categoryRepository = repository;
        this.categoryMapper = mapper;
    }

    @Override
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    public CategoryResponse getById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return categoryMapper.toResponse(category);
    }

    @Override
    public CategoryResponse create(CategoryRequest request) {
        Category category = categoryMapper.toDomain(request);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    public CategoryResponse update(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        category.setName(request.name());
        category.setDescription(request.description());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    public void delete(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category", id);
        }
        categoryRepository.deleteById(id);
    }
}

package org.example.springboot0.category.infrastructure;

import org.example.springboot0.category.domain.Category;
import org.example.springboot0.category.domain.ICategoryRepository;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class CategoryRepositoryAdapter implements ICategoryRepository {

    private final CategoryJpaRepository jpa;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Category> findAll() { return jpa.findAll(); }

    @Override
    public Optional<Category> findById(String id) { return jpa.findById(id); }

    @Override
    public Optional<Category> findByName(String name) { return jpa.findByNameIgnoreCase(name); }

    @Override
    public Category save(Category category) { return jpa.save(category); }

    @Override
    public Set<Category> findAllByIds(List<String> ids) { return new HashSet<>(jpa.findAllById(ids)); }

    @Override
    public boolean existsById(String id) { return jpa.existsById(id); }

    @Override
    public void deleteById(String id) { jpa.deleteById(id); }
}
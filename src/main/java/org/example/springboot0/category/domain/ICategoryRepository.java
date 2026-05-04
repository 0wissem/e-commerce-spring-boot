package org.example.springboot0.category.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ICategoryRepository {
    List<Category> findAll();
    Category save(Category category);
    Optional<Category> findById(String id);
    Optional<Category> findByName(String name);
    Set<Category> findAllByIds(List<String> ids);
    boolean existsById(String id);
    void deleteById(String id);
}

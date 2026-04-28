package org.example.springboot0.category.infrastructure;

import org.example.springboot0.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CategoryJpaRepository extends JpaRepository<Category, String> {
    Optional<Category> findByNameIgnoreCase(String name);
}
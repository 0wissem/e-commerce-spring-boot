package org.example.productservice.category.infrastructure;

import org.example.productservice.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CategoryJpaRepository extends JpaRepository<Category, String> {
    Optional<Category> findByNameIgnoreCase(String name);
}
package org.example.springboot0.product.infrastructure;

import org.example.springboot0.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ProductJpaRepository extends JpaRepository<Product, String> {
    Optional<Product> findByNameIgnoreCase(String name);
}
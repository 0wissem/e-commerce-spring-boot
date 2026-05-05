package org.example.springboot0.product.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IProductRepository {
    List<Product> findAll();
    Page<Product> findAll(Pageable pageable);
    Optional<Product> findById(String id);
    Optional<Product> findByName(String name);
    Product save(Product product);
    boolean existsById(String id);
    void deleteById(String id);
    Page<Product> search(String query, Double minPrice, Double maxPrice, String categoryId, Boolean inStock, Pageable pageable);
}
package org.example.springboot0.product.domain;

import java.util.List;
import java.util.Optional;

public interface IProductRepository {
    List<Product> findAll();
    Optional<Product> findById(String id);
    Optional<Product> findByName(String name);
    Product save(Product product);
    boolean existsById(String id);
    void deleteById(String id);
}
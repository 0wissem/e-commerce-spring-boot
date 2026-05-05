package org.example.springboot0.product.infrastructure;

import org.example.springboot0.product.domain.IProductRepository;
import org.example.springboot0.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepositoryAdapter implements IProductRepository {

    private final ProductJpaRepository jpa;

    public ProductRepositoryAdapter(ProductJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Product> findAll() { return jpa.findAll(); }

    @Override
    public Page<Product> findAll(Pageable pageable) { return jpa.findAll(pageable); }

    @Override
    public Optional<Product> findById(String id) { return jpa.findById(id); }

    @Override
    public Optional<Product> findByName(String name) { return jpa.findByNameIgnoreCase(name); }

    @Override
    public Product save(Product product) { return jpa.save(product); }

    @Override
    public boolean existsById(String id) { return jpa.existsById(id); }

    @Override
    public void deleteById(String id) { jpa.deleteById(id); }

    @Override
    public Page<Product> search(String query, Double minPrice, Double maxPrice, String categoryId, Boolean inStock, Pageable pageable) {
        return jpa.search(query, minPrice, maxPrice, categoryId, inStock, pageable);
    }
}
package org.example.productservice.category.domain;

import jakarta.persistence.*;
import org.example.productservice.product.domain.Product;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    private String id;

    @ManyToMany(mappedBy = "categories")
    private Set<Product> products = new HashSet<>();

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    public Category() {}

    public Category(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Set<Product> getProducts() { return products; }
    public void setProducts(Set<Product> products) { this.products = products; }
}
package org.example.springboot0.customer.domain;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A user (table `users`). Every user has a role; consumers are the customers, admins are
 * staff. The password is a BCrypt hash (empty for legacy rows that never set one).
 */
@Entity
@Table(name = "users")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CONSUMER;

    @Column(name = "phone", length = 32)
    private String phone;

    /**
     * SqlTypes.JSON rather than an AttributeConverter: a converter binds a String as varchar,
     * and Postgres will not implicitly cast varchar -> jsonb.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_address", columnDefinition = "jsonb")
    private Address defaultAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Customer() {}

    public Customer(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Address getDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(Address defaultAddress) { this.defaultAddress = defaultAddress; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

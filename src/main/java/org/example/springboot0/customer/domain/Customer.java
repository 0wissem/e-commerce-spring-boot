package org.example.springboot0.customer.domain;

import jakarta.persistence.*;

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

    public Customer() {}

    public Customer(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
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
}

package org.example.springboot0.customer.infrastructure;

import org.example.springboot0.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CustomerJpaRepository extends JpaRepository<Customer, String> {
    Optional<Customer> findByEmailIgnoreCase(String email);
}
package org.example.springboot0.customer.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ICustomerRepository {
    List<Customer> findAll();
    Page<Customer> findAll(Pageable pageable);
    Optional<Customer> findById(String id);
    Optional<Customer> findByEmail(String email);
    Customer save(Customer customer);
    boolean existsById(String id);
    void deleteById(String id);
}

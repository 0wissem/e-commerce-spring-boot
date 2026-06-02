package org.example.springboot0.customer.infrastructure;

import org.example.springboot0.customer.domain.Customer;
import org.example.springboot0.customer.domain.ICustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepositoryAdapter implements ICustomerRepository {

    private final CustomerJpaRepository jpa;

    public CustomerRepositoryAdapter(CustomerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Customer> findAll() { return jpa.findAll(); }

    @Override
    public Page<Customer> findAll(Pageable pageable) { return jpa.findAll(pageable); }

    @Override
    public Optional<Customer> findById(String id) { return jpa.findById(id); }

    @Override
    public Optional<Customer> findByEmail(String email) { return jpa.findByEmailIgnoreCase(email); }

    @Override
    public Customer save(Customer customer) { return jpa.save(customer); }

    @Override
    public boolean existsById(String id) { return jpa.existsById(id); }

    @Override
    public void deleteById(String id) { jpa.deleteById(id); }
}
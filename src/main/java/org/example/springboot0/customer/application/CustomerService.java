package org.example.springboot0.customer.application;

import org.example.springboot0.customer.application.dto.CustomerRequest;
import org.example.springboot0.customer.application.dto.CustomerResponse;
import org.example.springboot0.customer.domain.Customer;
import org.example.springboot0.customer.domain.ICustomerRepository;
import org.example.springboot0.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService implements ICustomerService {

    private final ICustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerService(ICustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    public List<CustomerResponse> getAll() {
        return customerRepository.findAll().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    public CustomerResponse getById(String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        return customerMapper.toResponse(customer);
    }

    @Override
    public CustomerResponse getByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with email: " + email));
        return customerMapper.toResponse(customer);
    }

    @Override
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = customerMapper.toDomain(request);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Override
    public CustomerResponse update(String id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        customer.setName(request.name());
        customer.setEmail(request.email());
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Override
    public void delete(String id) {
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Customer", id);
        }
        customerRepository.deleteById(id);
    }
}

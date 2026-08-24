package org.example.springboot0.customer.application;

import org.example.springboot0.customer.application.dto.CustomerRequest;
import org.example.springboot0.customer.application.dto.CustomerResponse;
import org.example.springboot0.customer.domain.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getRole(),
                customer.getPhone(),
                customer.getDefaultAddress(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    public Customer toDomain(CustomerRequest request) {
        Customer customer = new Customer(null, request.name(), request.email());
        applyOptionalFields(customer, request);
        return customer;
    }

    /** Shared by create and update: only overwrite what the caller actually sent. */
    public void applyOptionalFields(Customer customer, CustomerRequest request) {
        if (request.phone() != null)          customer.setPhone(request.phone());
        if (request.defaultAddress() != null) customer.setDefaultAddress(request.defaultAddress());
    }
}
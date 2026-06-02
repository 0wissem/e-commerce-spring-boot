package org.example.springboot0.customer.application;

import org.example.springboot0.customer.application.dto.CustomerRequest;
import org.example.springboot0.customer.application.dto.CustomerResponse;
import org.example.springboot0.shared.response.PageResponse;

import java.util.List;

public interface ICustomerService {
    List<CustomerResponse> getAll();
    PageResponse<CustomerResponse> getAll(int page, int size);
    CustomerResponse getById(String id);
    CustomerResponse getByEmail(String email);
    CustomerResponse create(CustomerRequest request);
    CustomerResponse update(String id, CustomerRequest request);
    void delete(String id);
}

package org.example.springboot0.customer.application;

import org.example.springboot0.customer.application.dto.CustomerRequest;
import org.example.springboot0.customer.application.dto.CustomerResponse;
import org.example.springboot0.customer.domain.Customer;
import org.example.springboot0.customer.domain.ICustomerRepository;
import org.example.springboot0.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for CustomerService — repository mocked, real mapper.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private ICustomerRepository customerRepository;
    private final CustomerMapper customerMapper = new CustomerMapper();

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepository, customerMapper);
    }

    @Test
    @DisplayName("getById: returns the mapped customer when found")
    void getById_found() {
        when(customerRepository.findById("c1"))
                .thenReturn(Optional.of(new Customer("c1", "Alice", "alice@example.com")));

        CustomerResponse response = service.getById("c1");

        assertThat(response.id()).isEqualTo("c1");
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("getById: throws when missing")
    void getById_missing() {
        when(customerRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    @DisplayName("create: saves and returns the customer")
    void create_saves() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = service.create(new CustomerRequest("Bob", "bob@example.com"));

        assertThat(response.name()).isEqualTo("Bob");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("update: applies changes and saves")
    void update_changes() {
        Customer existing = new Customer("c1", "Old", "old@example.com");
        when(customerRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = service.update("c1", new CustomerRequest("New", "new@example.com"));

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.email()).isEqualTo("new@example.com");
        verify(customerRepository).save(existing);
    }

    @Test
    @DisplayName("delete: throws and never deletes when missing")
    void delete_missing() {
        when(customerRepository.existsById("nope")).thenReturn(false);

        assertThatThrownBy(() -> service.delete("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(customerRepository, never()).deleteById(anyString());
    }
}

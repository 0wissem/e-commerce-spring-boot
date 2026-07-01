package org.example.springboot0.customer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springboot0.customer.application.ICustomerService;
import org.example.springboot0.customer.application.dto.CustomerRequest;
import org.example.springboot0.customer.application.dto.CustomerResponse;
import org.example.springboot0.customer.domain.Role;
import org.example.springboot0.shared.exception.GlobalExceptionHandler;
import org.example.springboot0.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test for CustomerController with standalone MockMvc.
 */
class CustomerControllerTest {

    private final ICustomerService customerService = mock(ICustomerService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerController(customerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/customers/{id} → 200 with the customer JSON")
    void getById_returns200() throws Exception {
        when(customerService.getById("c1"))
                .thenReturn(new CustomerResponse("c1", "Alice", "alice@example.com", Role.CONSUMER));

        mockMvc.perform(get("/api/customers/c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("c1"))
                .andExpect(jsonPath("$.data.name").value("Alice"));
    }

    @Test
    @DisplayName("GET /api/customers/{id} → 404 when the service throws not-found")
    void getById_returns404() throws Exception {
        when(customerService.getById("nope")).thenThrow(new ResourceNotFoundException("Customer", "nope"));

        mockMvc.perform(get("/api/customers/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/customers → 201 when the body is valid")
    void create_returns201() throws Exception {
        when(customerService.create(any()))
                .thenReturn(new CustomerResponse("c2", "Bob", "bob@example.com", Role.CONSUMER));
        CustomerRequest request = new CustomerRequest("Bob", "bob@example.com");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Customer created"))
                .andExpect(jsonPath("$.data.name").value("Bob"));
    }

    @Test
    @DisplayName("POST /api/customers → 400 when name is blank and email is invalid")
    void create_returns400() throws Exception {
        // @NotBlank name + @Email email both fail → 400 from GlobalExceptionHandler
        String invalidJson = """
                { "name": "", "email": "not-an-email" }
                """;

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}

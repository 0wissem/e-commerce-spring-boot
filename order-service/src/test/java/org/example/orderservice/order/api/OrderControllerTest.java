package org.example.orderservice.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.orderservice.order.application.IOrderService;
import org.example.orderservice.order.application.dto.OrderItemRequest;
import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.application.dto.OrderResponse;
import org.example.orderservice.order.domain.OrderStatus;
import org.example.orderservice.shared.exception.GlobalExceptionHandler;
import org.example.orderservice.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test for OrderController with standalone MockMvc (controller + exception
 * advice + validator, no Spring context). Mirrors the product-service pattern.
 */
class OrderControllerTest {

    private final IOrderService orderService = mock(IOrderService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/orders/{id} → 200 with the order JSON")
    void getById_returns200() throws Exception {
        when(orderService.getById("o1"))
                .thenReturn(new OrderResponse("o1", "cust-1", "Alice", OrderStatus.PENDING, 120.0, List.of()));

        mockMvc.perform(get("/api/orders/o1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("o1"))
                .andExpect(jsonPath("$.data.customerName").value("Alice"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} → 404 when the service throws not-found")
    void getById_returns404() throws Exception {
        when(orderService.getById("nope")).thenThrow(new ResourceNotFoundException("Order", "nope"));

        mockMvc.perform(get("/api/orders/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/orders → 201 when the body is valid")
    void create_returns201() throws Exception {
        when(orderService.create(any()))
                .thenReturn(new OrderResponse("o2", "cust-1", "Alice", OrderStatus.PENDING, 200.0, List.of()));
        OrderRequest request = new OrderRequest("cust-1", List.of(new OrderItemRequest("prod-1", 2)));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Order created"))
                .andExpect(jsonPath("$.data.id").value("o2"));
    }

    @Test
    @DisplayName("POST /api/orders → 400 when customerId is blank and items is empty")
    void create_returns400() throws Exception {
        // @NotBlank customerId + @NotEmpty items both fail → 400 from GlobalExceptionHandler
        String invalidJson = """
                { "customerId": "", "items": [] }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}

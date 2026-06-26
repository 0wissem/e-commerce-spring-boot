package org.example.productservice.product.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.productservice.product.application.IProductService;
import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.shared.exception.GlobalExceptionHandler;
import org.example.productservice.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-layer test with STANDALONE MockMvc: wires just the controller + the exception
 * advice + a validator — no Spring context, no server, no DB. MockMvc fires fake HTTP
 * requests and asserts on the status + JSON.
 *
 * NOTE: on Spring Boot 3.x you'd normally use @WebMvcTest(ProductController.class) +
 * @MockitoBean (the idiomatic "web slice"). Boot 4.0 moved that slice into a separate
 * module that isn't on this classpath, so we use the equivalent standalone setup —
 * the MockMvc API and assertions are identical; only the wiring differs.
 */
class ProductControllerTest {

    private final IProductService productService = mock(IProductService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet(); // initialise so @Valid is actually enforced

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new GlobalExceptionHandler()) // so exceptions → 404/400
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/products/{id} → 200 with the product JSON")
    void getById_returns200() throws Exception {
        ProductResponse product = new ProductResponse("p1", "Keyboard", 100.0, 119.0, 5, Set.of());
        when(productService.getById("p1")).thenReturn(product);

        mockMvc.perform(get("/api/products/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("p1"))
                .andExpect(jsonPath("$.data.name").value("Keyboard"));
    }

    @Test
    @DisplayName("GET /api/products/{id} → 404 when the service throws not-found")
    void getById_returns404_whenNotFound() throws Exception {
        when(productService.getById("nope"))
                .thenThrow(new ResourceNotFoundException("Product", "nope"));

        mockMvc.perform(get("/api/products/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product not found with id: nope"));
    }

    @Test
    @DisplayName("POST /api/products → 201 when the body is valid")
    void create_returns201_whenValid() throws Exception {
        ProductRequest request = new ProductRequest("Mouse", 50.0, 10, null);
        ProductResponse created = new ProductResponse("p2", "Mouse", 50.0, 59.5, 10, Set.of());
        when(productService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Product created"))
                .andExpect(jsonPath("$.data.name").value("Mouse"));
    }

    @Test
    @DisplayName("POST /api/products → 400 when name is blank and price is not positive")
    void create_returns400_whenInvalid() throws Exception {
        // blank name (@NotBlank) + negative price (@Positive) → bean validation fails
        // → MethodArgumentNotValidException → GlobalExceptionHandler maps it to 400
        String invalidJson = """
                { "name": "", "price": -5, "stockQuantity": 1 }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.name").exists())   // field error for 'name'
                .andExpect(jsonPath("$.data.price").exists());  // field error for 'price'
    }
}

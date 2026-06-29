package org.example.productservice.category.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.productservice.category.application.ICategoryService;
import org.example.productservice.category.application.dto.CategoryRequest;
import org.example.productservice.category.application.dto.CategoryResponse;
import org.example.productservice.shared.exception.GlobalExceptionHandler;
import org.example.productservice.shared.exception.ResourceNotFoundException;
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

/** Web-layer test for CategoryController with standalone MockMvc. */
class CategoryControllerTest {

    private final ICategoryService categoryService = mock(ICategoryService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoryController(categoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/categories → 200 with the list")
    void getAll_returns200() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of(new CategoryResponse("c1", "Electronics", "gadgets")));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Electronics"));
    }

    @Test
    @DisplayName("GET /api/categories/{id} → 404 when not found")
    void getById_returns404() throws Exception {
        when(categoryService.getById("nope")).thenThrow(new ResourceNotFoundException("Category", "nope"));

        mockMvc.perform(get("/api/categories/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/categories → 201 when valid")
    void create_returns201() throws Exception {
        when(categoryService.create(any())).thenReturn(new CategoryResponse("c2", "Books", "reading"));
        CategoryRequest request = new CategoryRequest("Books", "reading");

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Category created"))
                .andExpect(jsonPath("$.data.name").value("Books"));
    }

    @Test
    @DisplayName("POST /api/categories → 400 when name and description are blank")
    void create_returns400() throws Exception {
        String invalidJson = """
                { "name": "", "description": "" }
                """;

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}

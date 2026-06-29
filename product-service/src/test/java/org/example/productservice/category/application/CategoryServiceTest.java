package org.example.productservice.category.application;

import org.example.productservice.category.application.dto.CategoryRequest;
import org.example.productservice.category.application.dto.CategoryResponse;
import org.example.productservice.category.domain.Category;
import org.example.productservice.category.domain.ICategoryRepository;
import org.example.productservice.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit test for CategoryService — repository mocked, real mapper. */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private ICategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper = new CategoryMapper();

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, categoryMapper);
    }

    @Test
    @DisplayName("getAll: maps all categories")
    void getAll() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                new Category("c1", "Electronics", "gadgets"),
                new Category("c2", "Books", "reading")));

        List<CategoryResponse> all = service.getAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(CategoryResponse::name).containsExactly("Electronics", "Books");
    }

    @Test
    @DisplayName("getById: returns the mapped category when found")
    void getById_found() {
        when(categoryRepository.findById("c1"))
                .thenReturn(Optional.of(new Category("c1", "Electronics", "gadgets")));

        CategoryResponse r = service.getById("c1");

        assertThat(r.id()).isEqualTo("c1");
        assertThat(r.name()).isEqualTo("Electronics");
        assertThat(r.description()).isEqualTo("gadgets");
    }

    @Test
    @DisplayName("getById: throws when missing")
    void getById_missing() {
        when(categoryRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    @DisplayName("create: saves and returns the category")
    void create() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse r = service.create(new CategoryRequest("Electronics", "gadgets"));

        assertThat(r.name()).isEqualTo("Electronics");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("update: applies changes and saves")
    void update() {
        Category existing = new Category("c1", "Old", "oldDesc");
        when(categoryRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse r = service.update("c1", new CategoryRequest("New", "newDesc"));

        assertThat(r.name()).isEqualTo("New");
        assertThat(r.description()).isEqualTo("newDesc");
        verify(categoryRepository).save(existing);
    }

    @Test
    @DisplayName("delete: deletes when the category exists")
    void delete_exists() {
        when(categoryRepository.existsById("c1")).thenReturn(true);

        service.delete("c1");

        verify(categoryRepository).deleteById("c1");
    }

    @Test
    @DisplayName("delete: throws and never deletes when missing")
    void delete_missing() {
        when(categoryRepository.existsById("nope")).thenReturn(false);

        assertThatThrownBy(() -> service.delete("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).deleteById(anyString());
    }
}

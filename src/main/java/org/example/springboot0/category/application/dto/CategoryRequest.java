package org.example.springboot0.category.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank(message = "Name must not be blank") String name,
        @NotBlank(message = "Description must not be blank") String description
) {}
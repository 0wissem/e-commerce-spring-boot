package org.example.springboot0.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email must not be blank") String email,
        @NotBlank(message = "Password must not be blank") String password
) {}

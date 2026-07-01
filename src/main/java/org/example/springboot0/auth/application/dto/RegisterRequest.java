package org.example.springboot0.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name must not be blank") String name,
        @NotBlank(message = "Email must not be blank") @Email(message = "Must be a valid email") String email,
        @NotBlank(message = "Password must not be blank") @Size(min = 6, message = "Password must be at least 6 characters") String password
) {}

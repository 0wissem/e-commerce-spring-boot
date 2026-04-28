package org.example.springboot0.customer.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank(message = "Name must not be blank") String name,
        @NotBlank(message = "Email must not be blank") @Email(message = "Must be a valid email") String email
) {}
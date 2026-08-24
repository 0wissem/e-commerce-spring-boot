package org.example.springboot0.customer.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.example.springboot0.customer.domain.Address;

public record CustomerRequest(
        @NotBlank(message = "Name must not be blank") String name,
        @NotBlank(message = "Email must not be blank") @Email(message = "Must be a valid email") String email,

        // Optional — absent means "leave unchanged", so existing clients are unaffected.
        @Size(max = 32, message = "Phone must be at most 32 characters")
        @Pattern(regexp = "^$|^[+0-9 ().-]{6,32}$", message = "Phone contains invalid characters")
        String phone,

        Address defaultAddress
) {}

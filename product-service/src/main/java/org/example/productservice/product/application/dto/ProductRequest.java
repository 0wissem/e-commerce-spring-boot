package org.example.productservice.product.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotBlank(message = "Name must not be blank") String name,

        // Optional enrichment fields — absent means "leave unset", so existing clients
        // that only send name/price/stock keep working unchanged.
        @Size(max = 120, message = "Brand must be at most 120 characters") String brand,
        @Size(max = 4000, message = "Description must be at most 4000 characters") String description,

        // BigDecimal, not Double: money must be exact decimal. Boxed so a missing field is
        // caught by @NotNull rather than silently parsed as 0 (the 2026-07-13 lesson).
        @NotNull(message = "Price must not be null")
        @Positive(message = "Price must be positive")
        @Digits(integer = 10, fraction = 2, message = "Price must have at most 2 decimal places")
        BigDecimal price,

        // Optional: defaults to EUR when absent, so existing clients keep working unchanged.
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be an ISO-4217 alpha-3 code")
        String currency,

        @NotNull(message = "Stock quantity must not be null")
        @Min(value = 0, message = "Stock quantity must be 0 or more")
        Integer stockQuantity,

        List<String> categoryIds
) {}

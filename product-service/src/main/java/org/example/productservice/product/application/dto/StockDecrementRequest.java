package org.example.productservice.product.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Boxed Integer, not int: a missing field must fail @NotNull validation rather than be
 * silently parsed as 0 by Jackson before validation ever runs.
 */
public record StockDecrementRequest(
        @NotNull(message = "Quantity must not be null")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {}

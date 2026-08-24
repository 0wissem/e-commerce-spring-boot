package org.example.springboot0.customer.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The customer's default address, stored as JSONB.
 *
 * Deliberately a COPY-SOURCE, not a shared reference: when an order is placed, the checkout
 * copies these values into the order's own shipping_address. If the customer later moves,
 * past orders must still show where they were actually shipped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Address(
        String line1,
        String line2,
        String city,
        String postalCode,
        String country
) {}

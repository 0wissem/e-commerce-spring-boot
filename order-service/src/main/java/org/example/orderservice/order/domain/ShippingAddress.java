package org.example.orderservice.order.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Shipping address as a value object, stored as JSONB.
 *
 * Why a document rather than six columns: an address is read as a whole and never queried
 * field-by-field, and — like {@link OrderProductSnapshot} — it must stay frozen at what the
 * customer entered when the order was placed. If they later move house, this order still
 * shipped to the old address.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) keeps existing rows readable when fields are
 * added later — the same forward-compatibility rule the product snapshot relies on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShippingAddress(
        String line1,
        String line2,
        String city,
        String postalCode,
        String country
) {}

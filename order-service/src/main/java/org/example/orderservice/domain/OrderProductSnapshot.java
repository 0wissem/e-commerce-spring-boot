package org.example.orderservice.order.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderProductSnapshot(
        String name,
        double price,
        List<CategorySnapshot> categories
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategorySnapshot(String id, String name) {}
}

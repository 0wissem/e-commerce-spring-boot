package org.example.springboot0.order.domain;

public record OrderProductSnapshot(
        String name,
        double price,
        java.util.List<String> categoryNames
) {}

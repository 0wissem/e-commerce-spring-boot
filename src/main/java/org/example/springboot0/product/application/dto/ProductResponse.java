package org.example.springboot0.product.application.dto;

public record ProductResponse(String id, String name, double price, double finalPrice, int stockQuantity) {}
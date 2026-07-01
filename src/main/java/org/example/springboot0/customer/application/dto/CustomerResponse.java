package org.example.springboot0.customer.application.dto;

import org.example.springboot0.customer.domain.Role;

public record CustomerResponse(String id, String name, String email, Role role) {}

package org.example.springboot0.customer.application.dto;

import org.example.springboot0.customer.domain.Address;
import org.example.springboot0.customer.domain.Role;

import java.time.Instant;

/** All new fields are additive — existing clients keep working. Never exposes the password. */
public record CustomerResponse(
        String id,
        String name,
        String email,
        Role role,
        String phone,
        Address defaultAddress,
        Instant createdAt,
        Instant updatedAt
) {}

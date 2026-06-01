package org.example.springboot0.order.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.springboot0.shared.event.CategoryDto;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderProductSnapshot(
        String name,
        double price,
        List<CategoryDto> categories
) {}

package org.example.orderservice.order.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class OrderProductSnapshotConverter implements AttributeConverter<OrderProductSnapshot, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(OrderProductSnapshot snapshot) {
        if (snapshot == null) return null;
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OrderProductSnapshot", e);
        }
    }

    @Override
    public OrderProductSnapshot convertToEntityAttribute(String json) {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, OrderProductSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize OrderProductSnapshot", e);
        }
    }
}

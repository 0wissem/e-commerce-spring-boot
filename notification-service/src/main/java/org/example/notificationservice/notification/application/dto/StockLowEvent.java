package org.example.notificationservice.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * notification-service's OWN copy of the stock.low contract — an inbound DTO, like a request body.
 *
 * Not imported from product-service: no shared jar, so neither service has to redeploy when the
 * other's internals change. The JSON field names are the contract, not a Java class.
 *
 * TOLERANT READER: unknown fields are ignored, so product-service can ADD a field (schemaVersion 2)
 * without breaking this consumer. The field is annotated explicitly even though Jackson 3 already
 * defaults to ignoring unknown properties — the intent must not hang on a library default.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockLowEvent(
        String eventId,
        int schemaVersion,
        String productId,
        String productName,
        int stockQuantity,
        int threshold,
        Instant occurredAt) {
}

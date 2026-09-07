package org.example.orderservice.order.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.orderservice.order.domain.InsufficientStockException;
import org.example.orderservice.shared.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class ProductServiceClient {

    private final RestClient restClient;

    public ProductServiceClient(@Value("${product.service.url}") String productServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    public ProductData getById(String id) {
        return restClient.get()
                .uri("/api/products/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new ResourceNotFoundException("Product", id);
                })
                .body(ProductResponse.class)
                .data();
    }

    /**
     * Reserves stock in product-service. product-service owns the row, so it also owns the
     * locking and the retry — this client just reports what came back.
     *
     * A 409 means the reservation was refused for a business reason (not enough stock, or
     * sustained lock contention). It is mapped to a dedicated exception so order creation can
     * fail with a meaningful message instead of a generic 500.
     */
    public ProductData decrementStock(String id, int quantity, String bearerToken) {
        return restClient.post()
                .uri("/api/products/{id}/stock/decrement", id)
                .headers(h -> { if (bearerToken != null) h.setBearerAuth(bearerToken); })
                .body(new StockDecrementRequest(quantity))
                .retrieve()
                .onStatus(status -> status.value() == 409, (request, response) -> {
                    throw new InsufficientStockException(id, quantity);
                })
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    throw new ResourceNotFoundException("Product", id);
                })
                // Anything else in the 4xx range is OUR fault, not a missing product.
                // Mapping every 4xx to "not found" reported a 403 as a 404 and sent me
                // debugging the wrong thing entirely.
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new IllegalStateException(
                            "product-service rejected the stock call for " + id
                                    + " with HTTP " + response.getStatusCode()
                                    + " — check the forwarded token and its authorities");
                })
                .body(ProductResponse.class)
                .data();
    }

    /** Compensating call — releases units reserved by a failed order. */
    public ProductData incrementStock(String id, int quantity, String bearerToken) {
        return restClient.post()
                .uri("/api/products/{id}/stock/increment", id)
                .headers(h -> { if (bearerToken != null) h.setBearerAuth(bearerToken); })
                .body(new StockDecrementRequest(quantity))
                .retrieve()
                .body(ProductResponse.class)
                .data();
    }

    public record StockDecrementRequest(int quantity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductResponse(ProductData data) {}

    /**
     * Mirrors product-service's ProductResponse. `price` is BigDecimal, not double — parsing
     * an exact JSON decimal into a double would reintroduce the rounding error at the very
     * boundary the snapshot is meant to freeze.
     *
     * sku/brand/currency are new in the enriched product model. They stay nullable so this
     * client keeps working against an instance of product-service that predates them.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductData(
            String id,
            String sku,
            String name,
            String brand,
            BigDecimal price,
            String currency,
            Set<CategoryInfo> categories
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryInfo(String id, String name) {}
}

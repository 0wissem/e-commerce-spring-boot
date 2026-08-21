package org.example.orderservice.order.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

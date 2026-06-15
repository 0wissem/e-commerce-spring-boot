package org.example.orderservice.order.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.orderservice.shared.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CustomerServiceClient {

    private final RestClient restClient;

    public CustomerServiceClient(@Value("${customer.service.url}") String customerServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(customerServiceUrl)
                .build();
    }

    public CustomerData getById(String id) {
        return restClient.get()
                .uri("/api/customers/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new ResourceNotFoundException("Customer", id);
                })
                .body(CustomerResponse.class)
                .data();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerResponse(CustomerData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerData(String id, String name, String email) {}
}

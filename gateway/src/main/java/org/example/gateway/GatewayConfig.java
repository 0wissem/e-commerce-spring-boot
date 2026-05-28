package org.example.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(
            RouteLocatorBuilder builder,
            @Value("${PRODUCT_SERVICE_URL:http://localhost:8081}") String productServiceUrl,
            @Value("${MONOLITH_URL:http://localhost:8080}") String monolithUrl) {

        return builder.routes()
                .route("product-service", r -> r
                        .path("/api/products/**", "/api/categories/**")
                        .uri(productServiceUrl))
                .route("monolith", r -> r
                        .path("/api/**")
                        .uri(monolithUrl))
                .build();
    }
}

package org.example.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Random;

// Runs before RouteToRequestUrlFilter (order 10000).
// On startup, caches the monolith route. On each request, synchronously switches
// GATEWAY_ROUTE_ATTR from product-service to monolith based on weight.
@Component
public class WeightedRoutingFilter implements GlobalFilter, Ordered {

    @Value("${PRODUCT_SERVICE_WEIGHT:1}")
    private int productServiceWeight;

    @Autowired
    private RouteLocator routeLocator;

    private final Random random = new Random();
    private volatile Route monolithRoute;

    @EventListener(ApplicationReadyEvent.class)
    public void cacheMonolithRoute() {
        routeLocator.getRoutes()
                .filter(r -> "monolith".equals(r.getId()))
                .next()
                .subscribe(r -> this.monolithRoute = r);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (monolithRoute != null) {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            if (route != null && "product-service".equals(route.getId())
                    && random.nextInt(100) >= productServiceWeight) {
                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, monolithRoute);
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 9999;
    }
}

package org.example.orderservice.order.application;

import org.example.orderservice.AbstractIntegrationTest;
import org.example.orderservice.order.application.dto.OrderItemRequest;
import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.domain.IOrderRepository;
import org.example.orderservice.order.infrastructure.CustomerServiceClient;
import org.example.orderservice.order.infrastructure.ProductServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * REGRESSION TEST for the second half of a bug that reached the running stack.
 *
 * order-service called product-service's stock endpoint with NO Authorization header, so every
 * order failed with a 403. The existing suite could not see it: ProductServiceClient is mocked
 * everywhere, so the absence of a header was invisible.
 *
 * The subtle part — and the reason this test exists rather than a one-line fix being trusted:
 * SecurityContextHolder is a ThreadLocal, and the product lookups run on a separate executor
 * via CompletableFuture. Reading the context INSIDE those tasks yields nothing. The token has
 * to be captured on the REQUEST thread, before the async hop, and passed down as a value.
 *
 * If someone later moves the capture inside the async block, the assertion below fails.
 */
class CallerTokenForwardingTest extends AbstractIntegrationTest {

    @Autowired private IOrderService orderService;
    @Autowired private IOrderRepository orderRepository;

    @MockitoBean private ProductServiceClient productServiceClient;
    @MockitoBean private CustomerServiceClient customerServiceClient;

    private static final String CUSTOMER = "cust-token";
    private static final String TOKEN_VALUE = "the-callers-jwt";

    private final Queue<String> tokensSeenByClient = new ConcurrentLinkedQueue<>();

    private ProductServiceClient.ProductData product(String id) {
        return new ProductServiceClient.ProductData(
                id, "SKU-" + id, "Product " + id, "ACME",
                new BigDecimal("10.00"), "EUR", Set.of());
    }

    @BeforeEach
    void setUp() {
        tokensSeenByClient.clear();

        when(customerServiceClient.getById(anyString()))
                .thenReturn(new CustomerServiceClient.CustomerData(CUSTOMER, "Alice", "a@e.com"));
        when(productServiceClient.getById(anyString()))
                .thenAnswer(inv -> product(inv.getArgument(0)));

        // Record whatever token the client is handed — null included.
        when(productServiceClient.decrementStock(anyString(), anyInt(), any())).thenAnswer(inv -> {
            tokensSeenByClient.add(String.valueOf((Object) inv.getArgument(2)));
            return product(inv.getArgument(0));
        });

        // Put a real JWT authentication on the REQUEST thread, as the security filter would.
        Jwt jwt = Jwt.withTokenValue(TOKEN_VALUE)
                .header("alg", "HS256")
                .subject(CUSTOMER)
                .claim("role", "CONSUMER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        orderRepository.findAll().forEach(o -> orderRepository.deleteById(o.getId()));
    }

    @Test
    @DisplayName("the caller's JWT reaches product-service — captured before the async hop")
    void callerTokenIsForwardedToProductService() {
        orderService.create(new OrderRequest(
                CUSTOMER, List.of(new OrderItemRequest("p1", 1)), null));

        // THE ASSERTION: not null, and the caller's actual token.
        // Before the fix this was "null" and product-service answered 403.
        assertThat(tokensSeenByClient)
                .as("the token forwarded to the stock endpoint")
                .containsExactly(TOKEN_VALUE);
    }

    @Test
    @DisplayName("an anonymous caller forwards no token rather than inventing one")
    void anonymousCallerForwardsNull() {
        SecurityContextHolder.clearContext();

        orderService.create(new OrderRequest(
                CUSTOMER, List.of(new OrderItemRequest("p1", 1)), null));

        // Honest degradation: no credentials in, no credentials out. product-service will
        // reject it with a 401, which is the correct outcome — not a fabricated identity.
        assertThat(tokensSeenByClient).containsExactly("null");
    }

    @Test
    @DisplayName("every line of a multi-line order carries the token")
    void tokenIsForwardedForEveryLine() {
        orderService.create(new OrderRequest(CUSTOMER, List.of(
                new OrderItemRequest("p1", 1),
                new OrderItemRequest("p2", 2),
                new OrderItemRequest("p3", 1)
        ), null));

        assertThat(tokensSeenByClient)
                .as("one forwarded token per reserved line")
                .containsExactly(TOKEN_VALUE, TOKEN_VALUE, TOKEN_VALUE);
    }
}

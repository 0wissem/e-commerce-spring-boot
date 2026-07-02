package org.example.orderservice;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves order-service validates the monolith's JWTs (resource server). Every /api/orders
 * endpoint requires a valid token. Tokens are minted here with the same shared secret.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderSecurityIntegrationTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef0123456789";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
        r.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
        r.add("jwt.secret", () -> SECRET);
    }

    @Value("${local.server.port}")
    private int port;
    private RestClient client;
    private JwtEncoder encoder;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    private String token() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "CONSUMER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    @DisplayName("GET /api/orders: no token → 401, valid token → 200")
    void getRequiresToken() {
        int noToken = client.get().uri("/api/orders").exchange((req, res) -> res.getStatusCode().value());
        assertThat(noToken).isEqualTo(401);

        int withToken = client.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(withToken).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /api/orders without a token → 401")
    void createRequiresToken() {
        Map<String, Object> body = Map.of("customerId", "c1", "items",
                List.of(Map.of("productId", "p1", "quantity", 1)));
        int status = client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(401);
    }
}

package org.example.productservice;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves product-service validates the monolith's JWTs (resource server). We mint tokens
 * here with the SAME shared secret to simulate the monolith. Reads are public; catalog
 * writes require an ADMIN role.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductSecurityIntegrationTest {

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

    private String tokenWithRole(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private int postProduct(String bearer) {
        Map<String, Object> body = Map.of("name", "Sec Test", "price", 10.0, "stock", 1);
        RestClient.RequestBodySpec req = client.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return req.body(body).exchange((request, response) -> response.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /api/products is public → 200")
    void getIsPublic() {
        int status = client.get().uri("/api/products").exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /api/products: no token → 401, CONSUMER → 403, ADMIN → not 401/403")
    void writeRequiresAdmin() {
        assertThat(postProduct(null)).isEqualTo(401);
        assertThat(postProduct(tokenWithRole("CONSUMER"))).isEqualTo(403);
        assertThat(postProduct(tokenWithRole("ADMIN"))).isNotIn(401, 403);
    }
}

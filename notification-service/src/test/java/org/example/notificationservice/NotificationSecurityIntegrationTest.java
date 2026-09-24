package org.example.notificationservice;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Notifications are ADMIN-only; the Prometheus endpoint is open for scraping.
 *
 * Kafka listeners are switched OFF in this context. Spring caches one context per configuration,
 * so this one lives alongside the consumer test's context; with listeners on, both would join the
 * same consumer group and split the partitions — the consumer test would randomly miss records.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.kafka.listener.auto-startup=false")
class NotificationSecurityIntegrationTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef0123456789";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
        r.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", AbstractIntegrationTest.KAFKA::getBootstrapServers);
        r.add("jwt.secret", () -> SECRET);
    }

    @Value("${local.server.port}")
    private int port;
    private RestClient client;
    private JwtEncoder encoder;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(SECRET.getBytes(), "HmacSHA256")));
    }

    @Test
    @DisplayName("GET /api/notifications: no token -> 401, CONSUMER -> 403, ADMIN -> 200")
    void listRequiresAdmin() {
        assertThat(status("/api/notifications", null)).isEqualTo(401);
        assertThat(status("/api/notifications", token("CONSUMER"))).isEqualTo(403);
        assertThat(status("/api/notifications", token("ADMIN"))).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /actuator/prometheus is public (Prometheus scrapes it without a token)")
    void prometheusIsScrapeable() {
        String body = client.get().uri("/actuator/prometheus").retrieve().body(String.class);
        assertThat(body).contains("jvm_memory_used_bytes");
    }

    private int status(String path, String bearer) {
        RestClient.RequestHeadersSpec<?> req = client.get().uri(path);
        if (bearer != null) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return req.exchange((request, response) -> response.getStatusCode().value());
    }

    private String token(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("test-user").issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("role", role).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}

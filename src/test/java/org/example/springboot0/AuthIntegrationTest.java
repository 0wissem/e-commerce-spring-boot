package org.example.springboot0;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the JWT auth flow over real HTTP (RANDOM_PORT). Reuses the shared Testcontainers
 * Postgres. register → token, login (right/wrong password) → 200/401, and a protected
 * write rejected without a token but accepted with one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    private int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }

    private String registerAndGetToken(String email, String password) throws Exception {
        String body = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Test", "email", email, "password", password))
                .retrieve().body(String.class);
        return mapper.readTree(body).path("data").path("token").asText();
    }

    @Test
    @DisplayName("register → 201 and returns a non-empty token")
    void register_returnsToken() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "secret123");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("login: correct password → 200, wrong password → 401")
    void login_flow() throws Exception {
        String email = uniqueEmail();
        registerAndGetToken(email, "secret123");

        int ok = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "secret123"))
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(ok).isEqualTo(200);

        int wrong = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "WRONG"))
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(wrong).isEqualTo(401);
    }

    @Test
    @DisplayName("public read works; protected write → 401 without token, not-401 with a valid token")
    void protectedEndpoint() throws Exception {
        int getStatus = client.get().uri("/api/customers")
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(getStatus).isEqualTo(200);

        Map<String, Object> newCustomer = Map.of("name", "Bob", "email", uniqueEmail());

        int noAuth = client.post().uri("/api/customers")
                .contentType(MediaType.APPLICATION_JSON).body(newCustomer)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(noAuth).isEqualTo(401);

        String token = registerAndGetToken(uniqueEmail(), "secret123");
        int withAuth = client.post().uri("/api/customers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(newCustomer)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(withAuth).isNotEqualTo(401);
    }
}

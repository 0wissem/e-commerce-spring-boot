package org.example.springboot0;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springboot0.customer.domain.Customer;
import org.example.springboot0.customer.domain.ICustomerRepository;
import org.example.springboot0.customer.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves role-based authorization on DELETE /api/customers/{id} (@PreAuthorize hasRole ADMIN):
 * a CONSUMER token is authenticated but forbidden (403), an ADMIN token succeeds (200).
 * The ADMIN user is created directly (register only mints CONSUMERs), then logged in for a token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleAuthorizationTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private ICustomerRepository users;
    @Autowired
    private PasswordEncoder encoder;
    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }

    private String tokenFrom(String path, Map<String, Object> body) throws Exception {
        String json = client.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(String.class);
        return mapper.readTree(json).path("data").path("token").asText();
    }

    private String createAdminAndLogin() throws Exception {
        String email = uniqueEmail();
        Customer admin = new Customer(null, "Admin", email);
        admin.setPassword(encoder.encode("admin123"));
        admin.setRole(Role.ADMIN);
        users.save(admin);
        return tokenFrom("/api/auth/login", Map.of("email", email, "password", "admin123"));
    }

    @Test
    @DisplayName("DELETE /api/customers/{id}: CONSUMER → 403, ADMIN → 200")
    void deleteRequiresAdmin() throws Exception {
        String adminToken = createAdminAndLogin();
        String consumerToken = tokenFrom("/api/auth/register",
                Map.of("name", "Consumer", "email", uniqueEmail(), "password", "secret123"));

        // a target user to delete
        String targetId = users.save(new Customer(null, "Target", uniqueEmail())).getId();

        // CONSUMER: authenticated but wrong role → 403
        int forbidden = client.delete().uri("/api/customers/{id}", targetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(forbidden).isEqualTo(403);

        // ADMIN → 200
        int ok = client.delete().uri("/api/customers/{id}", targetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(ok).isEqualTo(200);
    }
}

package org.example.productservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: ONE shared Postgres container (the "singleton
 * container" pattern). It's started once for the whole test run and reused by every
 * subclass — far faster than starting a container per test class. Testcontainers' Ryuk
 * sidecar stops it automatically when the JVM exits, so no manual teardown is needed.
 *
 * Any integration test just does `extends AbstractIntegrationTest` and gets a live,
 * Flyway-migrated Postgres wired into the Spring datasource.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

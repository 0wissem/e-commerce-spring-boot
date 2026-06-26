package org.example.productservice;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: does the whole Spring context start? Extends AbstractIntegrationTest so it
 * boots against the shared Testcontainers Postgres (the app needs a real DB + Flyway).
 */
class ProductServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}

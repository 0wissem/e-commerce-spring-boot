package org.example.springboot0;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the whole context starts (against the shared Testcontainers Postgres,
 * with Flyway applying the real schema). Extends AbstractIntegrationTest for the DB.
 */
class SpringBoot0ApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

}

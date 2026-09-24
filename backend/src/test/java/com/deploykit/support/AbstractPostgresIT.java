package com.deploykit.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real PostgreSQL: the "singleton container" pattern recommended by
 * Testcontainers, so a `mvn verify` run starts the database once for every subclass instead of once per class.
 *
 * <p>Deliberately <b>not</b> {@code @Testcontainers}/{@code @Container}: that annotation pair stops the container
 * after each test class, and the next class's cached Spring context (and its already-built {@code DataSource}) would
 * then keep pointing at the now-closed port. Starting it once in a static initialiser and never stopping it (the
 * Ryuk reaper cleans it up when the JVM exits) keeps the port stable for the whole run.
 */
public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

package com.deploykit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.deploykit.support.AbstractPostgresIT;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the whole application against a real PostgreSQL. Unit tests never touch a database, so this is the only
 * place that catches a migration that fails on real Postgres, or an entity that no longer matches the schema
 * ({@code ddl-auto: validate} only checks that at startup).
 */
@SpringBootTest
class FlywayMigrationIT extends AbstractPostgresIT {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        // Only needed because SecurityProperties fails the whole context otherwise; this test cares about Flyway.
        registry.add("deploykit.security.jwt.secret", () -> "integration-test-secret-integration-test-secret-32");
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void allMigrationsApplyCleanlyToARealDatabase() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer failed = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(5);
    }
}

package com.dbtraining.reconx.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class LiquibaseMigrationsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;

    @Test
    void liquibase_applied_all_expected_changesets() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog", Integer.class);
        // Day 1 = 9 changesets (init + schema + seed + views + audit)
        // Day 4 = +3 Envers tables. Day 5 = +1 deleted_at column. Total expected = 13.
        assertThat(applied).isGreaterThanOrEqualTo(13);

        Integer trades = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trades WHERE deleted_at IS NULL", Integer.class);
        assertThat(trades).isGreaterThanOrEqualTo(10);   // seed_data.sql
    }
}
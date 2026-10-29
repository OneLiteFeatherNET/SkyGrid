package net.onelitefeather.skygridng.adapters.persistence;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Test support: creates an empty database on a running Testcontainers PostgreSQL instance, so every
 * test gets its own schema and Flyway history without sharing state with another test.
 */
public final class FreshTestDatabase {

    private FreshTestDatabase() {
    }

    /** Creates a new, empty database on {@code container} and returns the settings to connect to it. */
    public static DatabaseConfig create(PostgreSQLContainer<?> container) {
        var name = "skygrid_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                        container.getJdbcUrl(), container.getUsername(), container.getPassword());
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create test database " + name, e);
        }
        return new DatabaseConfig(
                container.getHost(),
                container.getMappedPort(5432),
                name,
                container.getUsername(),
                container.getPassword());
    }
}

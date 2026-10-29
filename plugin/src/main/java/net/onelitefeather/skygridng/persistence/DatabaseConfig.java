package net.onelitefeather.skygridng.persistence;

import java.util.Optional;

/**
 * Connection settings for a Postgres-backed adapter's database, read from environment variables
 * only: never a config file, never hardcoded, never logged. {@code user} and {@code password} have
 * no safe default and must be provided.
 *
 * @param host     the database host, defaults to {@code localhost}
 * @param port     the database port, defaults to {@code 5432}
 * @param name     the database name, defaults to {@code skygridng}
 * @param user     the database user, no default
 * @param password the database password, no default
 */
public record DatabaseConfig(String host, int port, String name, String user, String password) {

    private static final String ENV_HOST = "SKYGRID_DB_HOST";
    private static final String ENV_PORT = "SKYGRID_DB_PORT";
    private static final String ENV_NAME = "SKYGRID_DB_NAME";
    private static final String ENV_USER = "SKYGRID_DB_USER";
    private static final String ENV_PASSWORD = "SKYGRID_DB_PASSWORD";

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_NAME = "skygridng";

    public DatabaseConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("DatabaseConfig.host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("DatabaseConfig.port must be a valid TCP port (1-65535)");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DatabaseConfig.name must not be blank");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("DatabaseConfig.user must not be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("DatabaseConfig.password must not be null");
        }
    }

    /**
     * Reads the connection settings from environment variables: {@code SKYGRID_DB_HOST} (default
     * {@code localhost}), {@code SKYGRID_DB_PORT} (default {@code 5432}), {@code SKYGRID_DB_NAME}
     * (default {@code skygridng}), and the required {@code SKYGRID_DB_USER} /
     * {@code SKYGRID_DB_PASSWORD} pair, which has no safe default and fails fast if missing rather
     * than falling back to a guessable credential.
     *
     * @throws IllegalStateException if {@code SKYGRID_DB_USER} or {@code SKYGRID_DB_PASSWORD} is
     *                                not set
     */
    public static DatabaseConfig fromEnvironment() {
        var host = Optional.ofNullable(System.getenv(ENV_HOST)).orElse(DEFAULT_HOST);
        var port = Optional.ofNullable(System.getenv(ENV_PORT)).map(Integer::parseInt).orElse(DEFAULT_PORT);
        var name = Optional.ofNullable(System.getenv(ENV_NAME)).orElse(DEFAULT_NAME);
        var user = Optional.ofNullable(System.getenv(ENV_USER))
                .orElseThrow(() -> new IllegalStateException(
                        ENV_USER + " environment variable is required; there is no safe default for a database user"));
        var password = Optional.ofNullable(System.getenv(ENV_PASSWORD))
                .orElseThrow(() -> new IllegalStateException(
                        ENV_PASSWORD + " environment variable is required; there is no safe default for a database password"));
        return new DatabaseConfig(host, port, name, user, password);
    }

    /** The JDBC URL for this configuration. Never includes the user or password. */
    String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    /**
     * Redacts {@code password} so that logging, debugging, or exception messages built from this
     * record's default string form can never leak the plaintext credential.
     */
    @Override
    public String toString() {
        return "DatabaseConfig[host=" + host + ", port=" + port + ", name=" + name + ", user=" + user
                + ", password=REDACTED]";
    }
}

package net.onelitefeather.skygridng.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import net.onelitefeather.skygridng.challenge.ChallengeTierEntity;
import net.onelitefeather.skygridng.challenge.ChallengeTierId;
import net.onelitefeather.skygridng.season.SeasonId;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct coverage of the shared {@link Database} lifecycle: lazy bootstrap, the read and transaction
 * helpers, warm-up, the context class loader swap and the pool settings. The entity used here is the
 * real {@link ChallengeTierEntity}, because the single persistence unit only knows the real entities.
 */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseTest {

    private static final SeasonId SEASON = new SeasonId("s01");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static Database database;

    @BeforeAll
    static void startDatabase() {
        var config = FreshTestDatabase.create(POSTGRES);
        database = new Database(() -> config);
    }

    @AfterAll
    static void stopDatabase() {
        if (database != null) {
            database.close();
        }
    }

    private static DatabaseConfig freshConfig() {
        return FreshTestDatabase.create(POSTGRES);
    }

    /**
     * Proves construction is lazy: a poisoned {@code configSupplier} is never invoked by the constructor,
     * and only the first use resolves it, and therefore throws.
     */
    @Test
    void constructionNeverResolvesTheConfigSupplierOnlyFirstUseDoes() {
        var supplierInvoked = new AtomicBoolean(false);
        Supplier<DatabaseConfig> poisonedSupplier = () -> {
            supplierInvoked.set(true);
            throw new IllegalStateException("must not be resolved before first use");
        };

        var lazyDatabase = new Database(poisonedSupplier);

        assertThat(supplierInvoked).isFalse();

        assertThatThrownBy(() -> lazyDatabase.read(entityManager -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("must not be resolved before first use");
        assertThat(supplierInvoked).isTrue();
    }

    /** {@link Database#close()} is a no-op, not a null pointer exception, when the factory was never built. */
    @Test
    void closeIsSafeWhenTheFactoryWasNeverBuilt() {
        var neverUsedDatabase = new Database(() -> {
            throw new IllegalStateException("must not be called");
        });

        assertThatCode(neverUsedDatabase::close).doesNotThrowAnyException();
    }

    @Test
    void readFindsARowInsertedThroughInTransaction() {
        var id = new ChallengeTierId(SEASON, UUID.randomUUID(), "read_one");
        database.inTransaction(entityManager -> {
            entityManager.persist(new ChallengeTierEntity(id, 3));
            return null;
        });

        var found = database.read(entityManager -> entityManager.find(ChallengeTierEntity.class, id));

        assertThat(found).isNotNull();
        assertThat(found.getTier()).isEqualTo(3);
    }

    @Test
    void inTransactionCommitsOnSuccessSoTheRowIsVisibleToALaterRead() {
        var id = new ChallengeTierId(SEASON, UUID.randomUUID(), "commit_one");

        database.inTransaction(entityManager -> {
            entityManager.persist(new ChallengeTierEntity(id, 2));
            return null;
        });

        var found = database.read(entityManager -> entityManager.find(ChallengeTierEntity.class, id));
        assertThat(found).isNotNull();
        assertThat(found.getTier()).isEqualTo(2);
    }

    @Test
    void inTransactionRollsBackAndPropagatesTheOriginalExceptionWhenWorkThrows() {
        var id = new ChallengeTierId(SEASON, UUID.randomUUID(), "rollback_one");
        var failure = new RuntimeException("simulated failure after persist");

        assertThatThrownBy(() -> database.inTransaction(entityManager -> {
            entityManager.persist(new ChallengeTierEntity(id, 1));
            throw failure;
        })).isSameAs(failure);

        var found = database.read(entityManager -> entityManager.find(ChallengeTierEntity.class, id));
        assertThat(found).isNull();
    }

    /**
     * {@link Database#warmUp()} runs the lazy bootstrap on its own, so the first real call after it does
     * no bootstrap work: the config is resolved once, by warmUp, and never again.
     */
    @Test
    void warmUpBootstrapsSoTheNextCallReusesTheBuiltFactory() {
        var configResolutions = new AtomicInteger();
        var config = freshConfig();
        var warmedDatabase = new Database(() -> {
            configResolutions.incrementAndGet();
            return config;
        });
        try {
            warmedDatabase.warmUp();
            assertThat(configResolutions).as("warmUp must resolve the config and build the factory").hasValue(1);

            warmedDatabase.read(entityManager -> null);

            assertThat(configResolutions).as("a call after warmUp must reuse the built factory").hasValue(1);
        } finally {
            warmedDatabase.close();
        }
    }

    /**
     * The calling thread's context class loader can be one that sees none of the plugin's classes (on Paper
     * it is the server's loader). Flyway and the JPA provider look up their plugins through that loader, so
     * the first use must still bootstrap the schema and the factory with it in that state.
     */
    @Test
    void bootstrapsWhenTheContextClassLoaderCannotSeeThePlugin() {
        var id = new ChallengeTierId(SEASON, UUID.randomUUID(), "classloader_one");
        var originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        var freshDatabase = new Database(DatabaseTest::freshConfig);
        try {
            Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], null));

            assertThatCode(() -> freshDatabase.inTransaction(entityManager -> {
                entityManager.persist(new ChallengeTierEntity(id, 1));
                return null;
            })).as("first use must bootstrap Flyway and Hibernate without the context class loader")
                    .doesNotThrowAnyException();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            freshDatabase.close();
        }
    }

    /**
     * The factory's pool is the real HikariCP pool, so its settings are read directly: six connections at
     * most, one kept idle. Every repository shares this one pool.
     */
    @Test
    void theSharedPoolIsCappedAtSixConnectionsWithOneKeptIdle() {
        var freshDatabase = new Database(DatabaseTest::freshConfig);
        try {
            freshDatabase.warmUp();
            var factory = freshDatabase.read(EntityManager::getEntityManagerFactory);

            var pool = factory.unwrap(SessionFactoryImplementor.class)
                    .getServiceRegistry()
                    .getService(ConnectionProvider.class)
                    .unwrap(HikariDataSource.class);

            assertThat(pool.getMaximumPoolSize())
                    .as("the one pool must hold at most six connections")
                    .isEqualTo(6);
            assertThat(pool.getMinimumIdle())
                    .as("the pool must keep one connection idle instead of HikariCP's default of ten")
                    .isEqualTo(1);
        } finally {
            freshDatabase.close();
        }
    }
}

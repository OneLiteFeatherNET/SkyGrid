package net.onelitefeather.skygridng.adapters.persistence;

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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct coverage of {@link AbstractJpaRepository} itself, through the minimal, throwaway
 * {@link TestJpaRepository} subclass and {@link TestItemEntity}: this base class is now shared
 * infrastructure that every future Postgres-backed adapter inherits from, so it needs its own test
 * rather than relying only on {@code PostgresChallengeProgressRepositoryTest}'s indirect coverage.
 *
 * <p>Nothing here touches {@code ChallengeTierEntity}, its persistence unit, or its Flyway
 * migration; the test entity, persistence unit ({@code abstract-jpa-repository-test}), and
 * migration ({@code V1__create_test_item.sql}) are entirely separate, scoped to
 * {@code src/test/resources}.
 */
@Testcontainers
class AbstractJpaRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static TestJpaRepository repository;

    @BeforeAll
    static void startRepository() {
        var config = containerConfig();
        repository = new TestJpaRepository(() -> config);
    }

    private static DatabaseConfig containerConfig() {
        return new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    @AfterAll
    static void stopRepository() {
        if (repository != null) {
            repository.close();
        }
    }

    /**
     * Proves construction is lazy in both directions: a poisoned {@code configSupplier} that throws
     * as soon as it is invoked is never actually invoked by the constructor itself (no exception,
     * and the flag it sets stays false), and is only resolved, and therefore only throws, once
     * {@link AbstractJpaRepository#read} is actually called. This is a stronger proof of laziness
     * than merely timing construction: it directly observes that no connection settings are even
     * read, let alone a connection opened, before the first use.
     */
    @Test
    void constructionNeverResolvesTheConfigSupplierOnlyFirstUseDoes() {
        var supplierInvoked = new AtomicBoolean(false);
        Supplier<DatabaseConfig> poisonedSupplier = () -> {
            supplierInvoked.set(true);
            throw new IllegalStateException("must not be resolved before first use");
        };

        var lazyRepository = new TestJpaRepository(poisonedSupplier);

        assertThat(supplierInvoked).isFalse();

        assertThatThrownBy(() -> lazyRepository.read(entityManager -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("must not be resolved before first use");
        assertThat(supplierInvoked).isTrue();
    }

    /** {@link AbstractJpaRepository#close()} is a no-op, not a null pointer exception, when the factory was never built. */
    @Test
    void closeIsSafeWhenTheFactoryWasNeverBuilt() {
        var neverUsedRepository = new TestJpaRepository(
                () -> {
                    throw new IllegalStateException("must not be called");
                });

        assertThatCode(neverUsedRepository::close).doesNotThrowAnyException();
    }

    @Test
    void readFindsARowInsertedThroughInTransaction() {
        var id = "read-" + UUID.randomUUID();
        repository.inTransaction(entityManager -> {
            entityManager.persist(new TestItemEntity(id, "hello"));
            return null;
        });

        var found = repository.read(entityManager -> entityManager.find(TestItemEntity.class, id));

        assertThat(found).isNotNull();
        assertThat(found.getValue()).isEqualTo("hello");
    }

    @Test
    void inTransactionCommitsOnSuccessSoTheRowIsVisibleToALaterRead() {
        var id = "commit-" + UUID.randomUUID();

        repository.inTransaction(entityManager -> {
            entityManager.persist(new TestItemEntity(id, "committed"));
            return null;
        });

        var found = repository.read(entityManager -> entityManager.find(TestItemEntity.class, id));
        assertThat(found).isNotNull();
        assertThat(found.getValue()).isEqualTo("committed");
    }

    @Test
    void inTransactionRollsBackAndPropagatesTheOriginalExceptionWhenWorkThrows() {
        var id = "rollback-" + UUID.randomUUID();
        var failure = new RuntimeException("simulated failure after persist");

        assertThatThrownBy(() -> repository.inTransaction(entityManager -> {
            entityManager.persist(new TestItemEntity(id, "should not survive"));
            throw failure;
        })).isSameAs(failure);

        var found = repository.read(entityManager -> entityManager.find(TestItemEntity.class, id));
        assertThat(found).isNull();
    }

    /**
     * The calling thread's context class loader can be one that sees none of the plugin's classes
     * and service files (on Paper it is the server's loader). Flyway and the JPA provider look up
     * their plugins and {@code persistence.xml} through that loader, so the first use must still
     * bootstrap the schema and the entity manager factory with it in that state.
     */
    @Test
    void bootstrapsWhenTheContextClassLoaderCannotSeeThePlugin() {
        var id = "classloader-" + UUID.randomUUID();
        var originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        var freshRepository = new TestJpaRepository(AbstractJpaRepositoryTest::containerConfig);
        try {
            Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], null));

            assertThatCode(() -> freshRepository.inTransaction(entityManager -> {
                entityManager.persist(new TestItemEntity(id, "bootstrapped"));
                return null;
            })).as("first use must bootstrap Flyway and Hibernate without the context class loader")
                    .doesNotThrowAnyException();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            freshRepository.close();
        }
    }
}

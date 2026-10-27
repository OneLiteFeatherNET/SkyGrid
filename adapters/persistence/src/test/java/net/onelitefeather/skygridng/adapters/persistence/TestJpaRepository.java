package net.onelitefeather.skygridng.adapters.persistence;

import java.util.function.Supplier;

/**
 * Minimal concrete subclass used only to instantiate the abstract {@link AbstractJpaRepository} in
 * tests. It adds no behavior of its own: {@link AbstractJpaRepository#read} and
 * {@link AbstractJpaRepository#inTransaction} are {@code protected}, which already makes them
 * callable directly from {@link AbstractJpaRepositoryTest} since that test class lives in this same
 * package.
 */
final class TestJpaRepository extends AbstractJpaRepository {

    TestJpaRepository(Supplier<DatabaseConfig> configSupplier) {
        super(
                "abstract-jpa-repository-test",
                "classpath:db/migration",
                "flyway_history_abstract_jpa_repository_test",
                configSupplier);
    }
}

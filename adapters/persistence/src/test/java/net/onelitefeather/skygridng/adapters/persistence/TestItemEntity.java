package net.onelitefeather.skygridng.adapters.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Throwaway two-column entity, used only by {@link AbstractJpaRepositoryTest} to exercise
 * {@link AbstractJpaRepository}'s shared {@code read}/{@code inTransaction} lifecycle in isolation.
 * Its own persistence unit ({@code abstract-jpa-repository-test}) and Flyway migration
 * ({@code V1__create_test_item.sql}) live next to it under {@code src/test/resources}; neither this
 * entity nor its schema is ever wired into a real adapter.
 *
 * <p>Deliberately not a record, for the same reason as {@code ChallengeTierEntity} in
 * {@code adapters/challenge-progress}: Hibernate needs a mutable, proxyable class for its
 * dirty-checking machinery. The protected no-arg constructor exists for Hibernate only.
 */
@Entity
@Table(name = "test_item")
public class TestItemEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "value", nullable = false)
    private String value;

    /** For Hibernate's proxy and reflection machinery only; never call this from application code. */
    protected TestItemEntity() {
    }

    public TestItemEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public String getValue() {
        return value;
    }
}

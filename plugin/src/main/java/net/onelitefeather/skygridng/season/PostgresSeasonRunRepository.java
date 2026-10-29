package net.onelitefeather.skygridng.season;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import net.onelitefeather.skygridng.adapters.persistence.Database;
import org.hibernate.exception.ConstraintViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * PostgreSQL-backed {@link SeasonRunRepository}, persisted through Jakarta Persistence (JSR 338) via Hibernate
 * ORM on the shared {@link Database}. Schema ownership: the shared migrations in {@code classpath:db/migration}
 * (V6 and V7 for this table).
 *
 * <p>{@code switchActive} is one unit of work: it locks both rows, flushes the outgoing run to {@code CLOSING}
 * before the incoming run becomes {@code ACTIVE}, and so the partial unique index (the database's single-active
 * rule) is checked per statement in the right order. A conflict (optimistic lock or constraint violation) rolls
 * the whole unit back and is reported as {@link SeasonRunConflictException}.
 *
 * <p>Time comes only from the injected {@link Clock} (for {@code updated_at} on a plain save).
 */
public final class PostgresSeasonRunRepository implements SeasonRunRepository {

    private final Database database;
    private final Clock clock;

    /**
     * @param database the shared database this repository queries.
     * @param clock    the time source for {@code updated_at}; the platform binds the system UTC clock.
     */
    public PostgresSeasonRunRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override
    public Optional<SeasonRun> find(SeasonId seasonId) {
        return database.read(entityManager -> Optional.ofNullable(entityManager.find(SeasonRunEntity.class, seasonId.value()))
                .map(SeasonRunMapper::toRecord));
    }

    @Override
    public Optional<SeasonRun> active() {
        return database.read(entityManager -> entityManager
                .createNamedQuery("SeasonRunEntity.active", SeasonRunEntity.class)
                .setParameter("phase", SeasonPhase.ACTIVE)
                .getResultList()
                .stream()
                .findFirst()
                .map(SeasonRunMapper::toRecord));
    }

    @Override
    public List<SeasonRun> inFlight() {
        return database.read(entityManager -> entityManager
                .createNamedQuery("SeasonRunEntity.inFlight", SeasonRunEntity.class)
                .setParameter("excluded", List.of(SeasonPhase.ACTIVE, SeasonPhase.ARCHIVED))
                .getResultList()
                .stream()
                .map(SeasonRunMapper::toRecord)
                .toList());
    }

    @Override
    public void save(SeasonRun run) {
        var now = clock.instant();
        write(entityManager -> {
            var existing = entityManager.find(SeasonRunEntity.class, run.seasonId().value());
            if (existing == null) {
                entityManager.persist(SeasonRunMapper.toEntity(run, now));
            } else {
                SeasonRunMapper.copyInto(run, existing, now);
            }
            entityManager.flush();
        });
    }

    @Override
    public void switchActive(SeasonId from, SeasonId to, Instant at) {
        write(entityManager -> {
            if (from.equals(to)) {
                throw new SeasonRunConflictException("cannot switch season " + from.value() + " to itself");
            }
            var outgoing = from.equals(SeasonId.LEGACY) ? null
                    : entityManager.find(SeasonRunEntity.class, from.value(), LockModeType.PESSIMISTIC_WRITE);
            var incoming = entityManager.find(SeasonRunEntity.class, to.value(), LockModeType.PESSIMISTIC_WRITE);
            if ((outgoing == null && !from.equals(SeasonId.LEGACY)) || incoming == null) {
                throw new SeasonRunConflictException(
                        "cannot switch from " + from.value() + " to " + to.value() + ": both runs must exist");
            }
            if (outgoing != null) {
                outgoing.close(at);
            }
            // The outgoing run leaves ACTIVE before the incoming run may take it: the unique index is checked per statement.
            entityManager.flush();
            incoming.activate(at);
            entityManager.flush();
        });
    }

    /** Runs one write in its own transaction, reporting an optimistic lock or constraint conflict as a {@link SeasonRunConflictException}. */
    private void write(Consumer<EntityManager> work) {
        try {
            database.inTransaction(entityManager -> {
                work.accept(entityManager);
                return null;
            });
        } catch (RuntimeException e) {
            if (isConflict(e)) {
                throw new SeasonRunConflictException("season run write conflicted: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    private static boolean isConflict(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }
}

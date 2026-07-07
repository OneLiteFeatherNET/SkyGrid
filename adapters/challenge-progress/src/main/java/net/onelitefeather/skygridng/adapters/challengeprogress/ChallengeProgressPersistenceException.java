package net.onelitefeather.skygridng.adapters.challengeprogress;

/**
 * Wraps a persistence failure (a Flyway migration error, an entity manager factory bootstrap
 * failure, or a Jakarta Persistence/Hibernate exception from a query) as a single, stable unchecked
 * exception type, so that {@code jakarta.persistence}, Hibernate, and Flyway exception types never
 * appear on the public surface of {@link PostgresChallengeProgressRepository}, which implements the
 * framework-free {@code ports.out.ChallengeProgressRepository} port.
 */
public final class ChallengeProgressPersistenceException extends RuntimeException {

    public ChallengeProgressPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

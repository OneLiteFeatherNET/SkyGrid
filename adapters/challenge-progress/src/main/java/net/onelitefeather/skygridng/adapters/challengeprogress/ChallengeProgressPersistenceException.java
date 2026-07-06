package net.onelitefeather.skygridng.adapters.challengeprogress;

/**
 * Wraps a checked {@code java.sql.SQLException} as an unchecked exception, so that {@code java.sql}
 * types never appear on the public surface of {@link PostgresChallengeProgressRepository}, which
 * implements the framework-free {@code ports.out.ChallengeProgressRepository} port.
 */
public final class ChallengeProgressPersistenceException extends RuntimeException {

    public ChallengeProgressPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

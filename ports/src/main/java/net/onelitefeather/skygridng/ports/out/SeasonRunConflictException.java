package net.onelitefeather.skygridng.ports.out;

/**
 * Thrown by {@link SeasonRunRepository} when a write conflicts with the stored state: a stale copy of a run
 * was saved, or a switch would leave two runs active. The write was rolled back; the caller retries from the
 * persisted phase. Unchecked, so the core decides how to react.
 */
public class SeasonRunConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SeasonRunConflictException(String message) {
        super(message);
    }

    public SeasonRunConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

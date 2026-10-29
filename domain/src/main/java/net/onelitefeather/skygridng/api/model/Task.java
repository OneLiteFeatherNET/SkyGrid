package net.onelitefeather.skygridng.api.model;

/**
 * A single step of a {@link ChallengeRule}'s quest: something a player completes, with an optional
 * {@link Reward} granted on first completion.
 *
 * @param id          unique id within its owning challenge, referenced by completion tracking
 * @param description a short, human readable summary of what the task requires
 * @param reward      the reward granted on completion, or {@code null} for a task with no reward
 */
public record Task(String id, String description, Reward reward) {

    /** Mirrors {@link ChallengeRule#MAX_ID_LENGTH}: the same rationale applies to a task id. */
    public static final int MAX_ID_LENGTH = 64;

    public Task {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Task.id must not be blank");
        }
        if (id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Task.id must be at most " + MAX_ID_LENGTH + " characters");
        }
        description = description == null ? "" : description;
    }

    /** Whether this task grants a reward on completion. */
    public boolean hasReward() {
        return reward != null;
    }
}

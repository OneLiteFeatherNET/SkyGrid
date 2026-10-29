package net.onelitefeather.skygridng.api.model;

import java.util.List;
import net.onelitefeather.skygridng.generator.Rule;

/**
 * A single toggleable challenge: a player/session facing rule that restricts allowed actions
 * (for example "no crafting table"), completely separate from the per-block {@link Rule} model.
 *
 * @param id          unique id, referenced by admin commands and the season scheduler
 * @param description a short, human readable summary of what the challenge restricts
 * @param active      whether the challenge is currently enforced
 * @param tasks        the challenge's ordered quest steps, an immutable list, empty for a challenge
 *                     with no quest (for example {@code level_is_border})
 * @param sequential   whether {@code tasks} must be completed in list order; when {@code false} a
 *                     player may complete them in any order
 */
public record ChallengeRule(String id, String description, boolean active, List<Task> tasks, boolean sequential) {

    /**
     * The single source of truth for how long a challenge id may be, referenced by
     * {@link ChallengeTier} and by {@code adapters.challengeprogress.ChallengeTierId}'s
     * {@code challenge_id} column (bounded at the database with a matching check constraint in
     * {@code V2__bound_challenge_id_length.sql}), so all three stay in lockstep by construction
     * instead of by three separately maintained literals.
     */
    public static final int MAX_ID_LENGTH = 64;

    public ChallengeRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ChallengeRule.id must not be blank");
        }
        if (id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("ChallengeRule.id must be at most " + MAX_ID_LENGTH + " characters");
        }
        description = description == null ? "" : description;
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    /**
     * Convenience constructor for a challenge with no quest (no tasks, so {@code sequential} is
     * irrelevant and defaults to {@code false}). Kept so every pre-existing 3-argument call site
     * (for example {@code JacksonChallengeRepository}) keeps compiling unchanged.
     */
    public ChallengeRule(String id, String description, boolean active) {
        this(id, description, active, List.of(), false);
    }

    /** Returns a copy with a changed active state, all other fields unchanged. */
    public ChallengeRule withActive(boolean newActive) {
        return new ChallengeRule(id, description, newActive, tasks, sequential);
    }
}

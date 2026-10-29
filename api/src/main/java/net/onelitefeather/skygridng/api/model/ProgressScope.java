package net.onelitefeather.skygridng.api.model;

import java.util.UUID;

/**
 * The key of one season scoped progress row: the season, the player, the challenge and the prestige step.
 * Tier counters are keyed by the scope without the step, so a prestige step does not split a tier. The
 * prestige change consumes {@code prestigeStep}; until then every write uses step 0.
 *
 * @param season      the season the progress belongs to
 * @param playerId    the player the progress belongs to
 * @param challengeId the challenge id, following the {@link ChallengeRule} rules
 * @param prestigeStep the prestige step, between 0 and {@link #MAX_PRESTIGE_STEP} inclusive
 */
public record ProgressScope(SeasonId season, UUID playerId, String challengeId, int prestigeStep) {

    /** The highest prestige step of the prestige ladder. */
    public static final int MAX_PRESTIGE_STEP = 10;

    public ProgressScope {
        if (season == null) {
            throw new IllegalArgumentException("ProgressScope.season must not be null");
        }
        if (playerId == null) {
            throw new IllegalArgumentException("ProgressScope.playerId must not be null");
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("ProgressScope.challengeId must not be blank");
        }
        if (challengeId.length() > ChallengeRule.MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "ProgressScope.challengeId must be at most " + ChallengeRule.MAX_ID_LENGTH + " characters");
        }
        if (prestigeStep < 0 || prestigeStep > MAX_PRESTIGE_STEP) {
            throw new IllegalArgumentException(
                    "ProgressScope.prestigeStep must be between 0 and " + MAX_PRESTIGE_STEP);
        }
    }
}

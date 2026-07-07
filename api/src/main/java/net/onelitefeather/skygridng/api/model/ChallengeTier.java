package net.onelitefeather.skygridng.api.model;

import java.util.UUID;

/**
 * A player's progression tier for a single challenge (for example {@code one_life}), persisted across
 * restarts. Tier does not change challenge difficulty yet; it is tracked so that future scoring or
 * difficulty scaling can be layered on top without a schema change.
 *
 * @param playerId    the player this tier belongs to
 * @param challengeId the challenge id, matching {@link ChallengeRule#id()}
 * @param tier        the current tier, 0 meaning the player has never completed the challenge
 */
public record ChallengeTier(UUID playerId, String challengeId, int tier) {
    public ChallengeTier {
        if (playerId == null) {
            throw new IllegalArgumentException("ChallengeTier.playerId must not be null");
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("ChallengeTier.challengeId must not be blank");
        }
        if (tier < 0) {
            throw new IllegalArgumentException("ChallengeTier.tier must not be negative");
        }
    }
}

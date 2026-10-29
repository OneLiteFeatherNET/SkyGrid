package net.onelitefeather.skygridng.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import net.onelitefeather.skygridng.api.model.SeasonId;

import java.io.Serializable;
import java.util.UUID;

/**
 * The composite primary key of {@link ChallengeTierEntity}: one row per (season, player, challenge)
 * triple, matching the {@code PRIMARY KEY (season_id, player_id, challenge_id)} of the
 * {@code challenge_tier} table after {@code V3__scope_tier_by_season.sql}.
 *
 * <p>A record is a valid JPA embeddable id here (Hibernate 6.2+ supports records as embeddable
 * types): an id class only needs value semantics and {@link Serializable}.
 *
 * @param seasonId    the season the tier belongs to, column {@code season_id}
 * @param playerId    the player's UUID, column {@code player_id}
 * @param challengeId the challenge's id, column {@code challenge_id}
 */
@Embeddable
public record ChallengeTierId(
        @Column(name = "season_id", nullable = false) SeasonId seasonId,
        @Column(name = "player_id", nullable = false) UUID playerId,
        @Column(name = "challenge_id", nullable = false, length = ChallengeRule.MAX_ID_LENGTH) String challengeId)
        implements Serializable {

    public ChallengeTierId {
        if (seasonId == null) {
            throw new IllegalArgumentException("ChallengeTierId.seasonId must not be null");
        }
        if (playerId == null) {
            throw new IllegalArgumentException("ChallengeTierId.playerId must not be null");
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("ChallengeTierId.challengeId must not be blank");
        }
        if (challengeId.length() > ChallengeRule.MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "ChallengeTierId.challengeId must be at most " + ChallengeRule.MAX_ID_LENGTH + " characters");
        }
    }
}

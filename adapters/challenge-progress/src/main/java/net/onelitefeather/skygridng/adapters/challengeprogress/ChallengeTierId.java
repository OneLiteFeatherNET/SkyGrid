package net.onelitefeather.skygridng.adapters.challengeprogress;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

/**
 * The composite primary key of {@link ChallengeTierEntity}: one row per (player, challenge) pair,
 * matching the {@code PRIMARY KEY (player_id, challenge_id)} of the {@code challenge_tier} table
 * owned by {@code V1__create_challenge_tier.sql}.
 *
 * <p>A record is a valid JPA embeddable id here (Hibernate 6.2+ supports records as embeddable
 * types), unlike {@link ChallengeTierEntity} itself: an id class only needs value semantics
 * (immutability, and {@code equals}/{@code hashCode} over every attribute, both of which a record
 * gives for free) and {@link Serializable}, it does not need the mutable, proxyable shape that an
 * owning entity needs for Hibernate's dirty-checking machinery.
 *
 * @param playerId    the player's UUID, column {@code player_id}
 * @param challengeId the challenge's id, column {@code challenge_id}
 */
@Embeddable
public record ChallengeTierId(
        @Column(name = "player_id", nullable = false) UUID playerId,
        @Column(name = "challenge_id", nullable = false) String challengeId) implements Serializable {

    public ChallengeTierId {
        if (playerId == null) {
            throw new IllegalArgumentException("ChallengeTierId.playerId must not be null");
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("ChallengeTierId.challengeId must not be blank");
        }
    }
}

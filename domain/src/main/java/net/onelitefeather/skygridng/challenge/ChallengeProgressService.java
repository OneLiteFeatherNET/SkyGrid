package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.season.SeasonId;
import net.onelitefeather.skygridng.season.CurrentSeason;

import java.util.Map;
import java.util.UUID;

/**
 * Implements {@link ChallengeProgressUseCase} as a thin delegate to a {@link ChallengeProgressRepository},
 * resolving the current season per call through {@link CurrentSeason}.
 * Deliberately no caching layer, unlike {@link net.onelitefeather.skygridng.generator.RuleState} and
 * {@link net.onelitefeather.skygridng.challenge.ChallengeState}: correctness of tier progression across
 * multiple servers and restarts matters more here than avoiding a database round trip.
 */
public final class ChallengeProgressService implements ChallengeProgressUseCase {

    private final ChallengeProgressRepository repository;
    private final CurrentSeason currentSeason;

    public ChallengeProgressService(ChallengeProgressRepository repository, CurrentSeason currentSeason) {
        this.repository = repository;
        this.currentSeason = currentSeason;
    }

    @Override
    public int tierOf(UUID playerId, String challengeId) {
        return repository.tierOf(currentSeason.id(), playerId, challengeId);
    }

    @Override
    public int recordCompletion(UUID playerId, String challengeId) {
        return repository.recordCompletion(currentSeason.id(), playerId, challengeId);
    }

    @Override
    public Map<SeasonId, Integer> history(UUID playerId) {
        return repository.history(playerId);
    }

    @Override
    public void resetCurrentSeason() {
        repository.resetSeason(currentSeason.id());
    }
}

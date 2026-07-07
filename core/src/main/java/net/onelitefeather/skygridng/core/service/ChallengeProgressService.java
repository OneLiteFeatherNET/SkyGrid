package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.ports.in.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;

import java.util.UUID;

/**
 * Implements {@link ChallengeProgressUseCase} as a thin delegate to a {@link ChallengeProgressRepository}.
 * Deliberately no caching layer, unlike {@link net.onelitefeather.skygridng.core.state.RuleState} and
 * {@link net.onelitefeather.skygridng.core.state.ChallengeState}: correctness of tier progression across
 * multiple servers and restarts matters more here than avoiding a database round trip.
 */
public final class ChallengeProgressService implements ChallengeProgressUseCase {

    private final ChallengeProgressRepository repository;

    public ChallengeProgressService(ChallengeProgressRepository repository) {
        this.repository = repository;
    }

    @Override
    public int tierOf(UUID playerId, String challengeId) {
        return repository.tierOf(playerId, challengeId);
    }

    @Override
    public int recordCompletion(UUID playerId, String challengeId) {
        return repository.recordCompletion(playerId, challengeId);
    }

    @Override
    public void resetAll() {
        repository.resetAll();
    }
}

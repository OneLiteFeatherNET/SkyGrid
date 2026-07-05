package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.ChallengeCatalog;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.core.state.ChallengeState;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.out.ChallengeRepository;

import java.util.List;

/**
 * Implements in game administration of challenges. Deliberately simpler than {@link RuleAdminService}:
 * a toggle is one atomic step with no exploratory undo to protect, so {@link #activate} and
 * {@link #deactivate} persist immediately instead of deferring to a separate save step.
 */
public final class ChallengeAdminService implements ChallengeAdminUseCase {

    private final ChallengeState state;
    private final ChallengeRepository repository;

    public ChallengeAdminService(ChallengeState state, ChallengeRepository repository) {
        this.state = state;
        this.repository = repository;
    }

    @Override
    public List<ChallengeRule> list() {
        return List.copyOf(state.current().challenges().values());
    }

    @Override
    public boolean isActive(String id) {
        return state.current().forId(id).map(ChallengeRule::active).orElse(false);
    }

    @Override
    public boolean activate(String id) {
        return setActive(id, true);
    }

    @Override
    public boolean deactivate(String id) {
        return setActive(id, false);
    }

    private boolean setActive(String id, boolean active) {
        ChallengeCatalog catalog = state.current();
        var rule = catalog.forId(id);
        if (rule.isEmpty()) {
            return false;
        }
        ChallengeCatalog updated = catalog.with(rule.get().withActive(active));
        state.replace(updated);
        repository.save(updated);
        return true;
    }
}

package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.ChallengeCatalog;

/**
 * Out port: where the core loads the challenge catalog from and saves it to.
 *
 * The catalog holds the toggleable challenges (player/session facing restrictions, distinct from
 * the per-block {@link net.onelitefeather.skygridng.generator.RuleCatalog}). The adapter
 * implementation is just storage; another backend can be added without touching the core.
 */
public interface ChallengeRepository {

    /** Loads the challenge catalog. */
    ChallengeCatalog load();

    /** Persists the challenge catalog. */
    void save(ChallengeCatalog catalog);
}

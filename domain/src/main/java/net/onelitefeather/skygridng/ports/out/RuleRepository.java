package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.RuleCatalog;

/**
 * Out port: where the core loads the per-block rule catalog from and saves it to.
 *
 * The catalog holds one rule per block (block-data state, physics and optional placement), loaded from
 * the {@code rules/} directory. The Jackson implementation is just an adapter; another backend can be
 * added without touching the core.
 */
public interface RuleRepository {

    /** Loads the rule catalog (creates the bundled defaults if the {@code rules/} directory is empty). */
    RuleCatalog load();

    /** Persists the rule catalog (one file per block). */
    void save(RuleCatalog catalog);
}

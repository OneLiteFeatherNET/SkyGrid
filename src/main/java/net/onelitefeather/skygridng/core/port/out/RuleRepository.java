package net.onelitefeather.skygridng.core.port.out;

import net.onelitefeather.skygridng.core.model.GeneratorConfig;

/**
 * Out port: where the core loads the configuration from and saves it to.
 *
 * The JSON implementation is just an adapter. A database, web, or in memory variant can be added
 * without touching the core (dependency inversion).
 */
public interface RuleRepository {

    /** Loads the configuration (creates defaults if missing). */
    GeneratorConfig load();

    /** Persists the configuration. */
    void save(GeneratorConfig config);
}

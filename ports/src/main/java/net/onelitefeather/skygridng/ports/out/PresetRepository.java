package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.PresetCatalog;

/**
 * Out port: where the core loads the preset catalog from and saves it to.
 *
 * The catalog holds every named preset and which one is active. The Jackson implementation is just an
 * adapter; a database or in memory variant can be added without touching the core.
 */
public interface PresetRepository {

    /** Loads the catalog (creates the defaults if the file is missing). */
    PresetCatalog load();

    /** Persists the catalog (presets and the active selection). */
    void save(PresetCatalog catalog);
}

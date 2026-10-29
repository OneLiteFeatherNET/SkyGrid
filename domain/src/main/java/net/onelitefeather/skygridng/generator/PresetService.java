package net.onelitefeather.skygridng.generator;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the live preset catalog and applies presets to the generator.
 *
 * The {@code state} is the active/default state: the edit target the admin service mutates, and what
 * worlds with no explicit preset use. {@link #stateFor} hands out one live {@link RuleState} per
 * named preset so worlds that pick a preset through their generator spec ({@code SkyGrid-ng:<preset>})
 * each generate from it; those per-preset states are re-resolved whenever the shared catalog changes
 * (a save or a reload), so editing a preset propagates to every world that uses it. Activating a
 * preset only changes the active/default; per-preset states stay pinned to their preset.
 *
 * The admin service delegates {@code reload} and {@code save} here, so all preset I/O lives in one
 * place: a reload re-reads the file and re-resolves everything, and a save flattens the current
 * (possibly in game edited) config back into the active preset.
 */
public final class PresetService implements Presets {

    private final PresetRepository repository;
    private final RuleState state;
    private final Map<String, RuleState> presetStates = new ConcurrentHashMap<>();
    private final Map<String, String> worldAssignments = new ConcurrentHashMap<>();
    private PresetCatalog catalog;

    public PresetService(PresetRepository repository, RuleState state, PresetCatalog catalog) {
        this.repository = repository;
        this.state = state;
        this.catalog = catalog;
    }

    @Override
    public synchronized List<String> names() {
        return catalog.names();
    }

    @Override
    public synchronized String active() {
        return catalog.active();
    }

    @Override
    public synchronized boolean activate(String name) {
        String key = match(name);
        if (key == null) {
            return false;
        }
        state.replace(PresetResolver.resolve(catalog, key));
        catalog = catalog.withActive(key);
        repository.save(catalog);
        return true;
    }

    /** The catalog preset name matching {@code name} case-insensitively, or null if none does. */
    private String match(String name) {
        if (catalog.presets().containsKey(name)) {
            return name;
        }
        for (String key : catalog.presets().keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    /**
     * The live state for a named preset, created on first use and re-resolved when the catalog
     * changes. Worlds assigned this preset through their generator spec generate from it.
     */
    public synchronized RuleState stateFor(String name) {
        return presetStates.computeIfAbsent(name, id -> new RuleState(PresetResolver.resolve(catalog, id), 1));
    }

    /** Records which preset a world was assigned, for {@link #worlds()}. Called by the platform. */
    public void recordWorld(String world, String preset) {
        worldAssignments.put(world, preset);
    }

    @Override
    public Map<String, String> worlds() {
        return Map.copyOf(worldAssignments);
    }

    /** Re-reads the preset file and re-resolves the active preset and every per-preset state. */
    public synchronized void reload() {
        catalog = repository.load();
        state.replace(PresetResolver.resolve(catalog, catalog.active()));
        reResolveAll();
    }

    /** Flattens the current config into the active preset, persists the catalog, and propagates it. */
    public synchronized void save() {
        catalog = catalog.withPreset(PresetResolver.toPreset(catalog.active(), state.current()));
        repository.save(catalog);
        reResolveAll();
    }

    /** Pushes the current catalog into every per-preset state, so edits reach the worlds using them. */
    private void reResolveAll() {
        presetStates.forEach((name, presetState) -> {
            if (catalog.presets().containsKey(name)) {
                presetState.replace(PresetResolver.resolve(catalog, name));
            }
        });
    }
}

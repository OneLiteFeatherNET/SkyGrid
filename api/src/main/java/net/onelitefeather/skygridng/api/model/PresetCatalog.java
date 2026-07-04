package net.onelitefeather.skygridng.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The set of named presets plus which one is active. Insertion order is preserved so listings and
 * the saved file keep a stable order.
 *
 * @param presets the presets by id (ordered, non-empty)
 * @param active  the id of the active preset (must be a key of {@code presets})
 */
public record PresetCatalog(Map<String, Preset> presets, String active) {
    public PresetCatalog {
        if (presets == null || presets.isEmpty()) {
            throw new IllegalArgumentException("a preset catalog needs at least one preset");
        }
        presets = Collections.unmodifiableMap(new LinkedHashMap<>(presets));
        if (active == null || !presets.containsKey(active)) {
            throw new IllegalArgumentException("active preset must be one of " + presets.keySet() + ", was: " + active);
        }
    }

    /** The names of all presets, in their stored order. */
    public List<String> names() {
        return List.copyOf(presets.keySet());
    }

    /** Returns a copy with a different active preset. */
    public PresetCatalog withActive(String newActive) {
        return new PresetCatalog(presets, newActive);
    }

    /** Returns a copy with {@code preset} added or replaced (keeping it active). */
    public PresetCatalog withPreset(Preset preset) {
        Map<String, Preset> copy = new LinkedHashMap<>(presets);
        copy.put(preset.name(), preset);
        return new PresetCatalog(copy, active);
    }
}

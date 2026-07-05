package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.TileType;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;
import net.onelitefeather.skygridng.core.state.RuleState;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Implements in game administration. Validates input against the domain; changes go through
 * {@link RuleState}, which handles undo (Memento) and cache invalidation plus algorithm rebuilds
 * (Observer). Persistence (reload and save) is delegated to {@link PresetService}, which writes the
 * edits back into the active preset.
 */
public final class RuleAdminService implements RuleAdminUseCase {

    private final RuleState state;
    private final PresetService presets;
    private final TerrainAlgorithms algorithms;

    public RuleAdminService(RuleState state, PresetService presets, TerrainAlgorithms algorithms) {
        this.state = state;
        this.presets = presets;
        this.algorithms = algorithms;
    }

    @Override
    public GeneratorConfig current() {
        return state.current();
    }

    @Override
    public String algorithm() {
        return state.current().algorithm();
    }

    @Override
    public boolean setAlgorithm(String algorithmId) {
        if (!algorithms.isKnown(algorithmId)) {
            return false;
        }
        state.apply(state.current().withAlgorithm(algorithmId));
        return true;
    }

    @Override
    public Set<String> algorithmIds() {
        return algorithms.ids();
    }

    @Override
    public List<String> tileIds() {
        return state.current().wfc().tileIds();
    }

    @Override
    public Optional<TileType> tile(String id) {
        return state.current().wfc().tileById(id);
    }

    @Override
    public void reload() {
        presets.reload();
    }

    @Override
    public void save() {
        presets.save();
    }

    @Override
    public boolean setWeight(String tileId, double weight) {
        GeneratorConfig config = state.current();
        if (config.wfc().tileById(tileId).isEmpty() || weight <= 0.0) {
            return false;
        }
        state.apply(config.withWfc(config.wfc().withTileWeight(tileId, weight)));
        return true;
    }

    @Override
    public boolean allow(String tileA, String tileB) {
        GeneratorConfig config = state.current();
        if (config.wfc().tileById(tileA).isEmpty() || config.wfc().tileById(tileB).isEmpty()) {
            return false;
        }
        state.apply(config.withWfc(config.wfc().withAllowed(tileA, tileB)));
        return true;
    }

    @Override
    public boolean disallow(String tileA, String tileB) {
        GeneratorConfig config = state.current();
        if (config.wfc().tileById(tileA).isEmpty() || config.wfc().tileById(tileB).isEmpty()) {
            return false;
        }
        state.apply(config.withWfc(config.wfc().withoutAllowed(tileA, tileB)));
        return true;
    }

    @Override
    public boolean setSkyGridWeight(String material, double weight) {
        if (weight <= 0.0 || material == null || material.isBlank()) {
            return false;
        }
        BlockId block;
        try {
            block = BlockId.of(material);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        GeneratorConfig config = state.current();
        state.apply(config.withSkygrid(config.skygrid().withBlockWeight(block, weight)));
        return true;
    }

    @Override
    public boolean undo() {
        return state.undo();
    }
}

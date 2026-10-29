package net.onelitefeather.skygridng.generator;


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
    private final DefaultTerrainAlgorithms algorithms;

    public RuleAdminService(RuleState state, PresetService presets, DefaultTerrainAlgorithms algorithms) {
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
    public void reload() {
        presets.reload();
    }

    @Override
    public void save() {
        presets.save();
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

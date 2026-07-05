package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.core.algorithm.IndependentSkyGridAlgorithm;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.core.util.Registry;
import net.onelitefeather.skygridng.ports.algorithm.AlgorithmFactory;
import net.onelitefeather.skygridng.ports.algorithm.TerrainAlgorithm;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;

import java.util.Map;
import java.util.Set;

/**
 * The standard {@link TerrainAlgorithms}.
 *
 * A {@link Registry} maps each algorithm id to an {@link AlgorithmFactory} (Factory Method), so the
 * registry's key set is the single source of truth for which ids are known: lookup, {@link #isKnown}
 * and {@link #ids} all read it and cannot drift. The factories are contributed from outside (a Guice
 * MapBinder in the composition root), so a distribution can add an algorithm without changing this
 * class. The active algorithm is rebuilt whenever the configuration changes (Observer on
 * {@link RuleState}); an unknown id falls back to the {@link IndependentSkyGridAlgorithm}.
 */
public final class DefaultTerrainAlgorithms implements TerrainAlgorithms {

    private final RuleState state;
    private final Registry<String, AlgorithmFactory> registry;
    private volatile TerrainAlgorithm current;

    public DefaultTerrainAlgorithms(RuleState state, Map<String, AlgorithmFactory> factories) {
        this.state = state;
        this.registry = new Registry<>();
        factories.forEach(this.registry::register);
        rebuild();
        state.addChangeListener(this::rebuild);
    }

    @Override
    public TerrainAlgorithm current() {
        return current;
    }

    @Override
    public boolean isKnown(String id) {
        return registry.contains(id);
    }

    @Override
    public Set<String> ids() {
        return registry.keys();
    }

    private void rebuild() {
        GeneratorConfig config = state.current();
        AlgorithmFactory factory = registry.find(config.algorithm())
                .orElseGet(() -> registry.find(IndependentSkyGridAlgorithm.ID).orElseThrow());
        this.current = factory.create(config);
    }
}

package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.core.algorithm.IndependentSkyGridAlgorithm;
import net.onelitefeather.skygridng.core.algorithm.NoiseSkyGridAlgorithm;
import net.onelitefeather.skygridng.ports.algorithm.TerrainAlgorithm;
import net.onelitefeather.skygridng.core.algorithm.WfcSurfaceAlgorithm;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.ports.in.GenerateRegionUseCase;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithmProvider;
import net.onelitefeather.skygridng.core.state.RuleState;

/**
 * Builds the active {@link TerrainAlgorithm} from the configuration (Factory) and rebuilds it
 * whenever the configuration changes (Observer on {@link RuleState}).
 *
 * This is the single place that maps the {@code algorithm} config value to an implementation.
 * Adding an algorithm adds one branch here.
 */
public final class AlgorithmService implements TerrainAlgorithmProvider {

    private final RuleState state;
    private final GenerateRegionUseCase region;
    private volatile TerrainAlgorithm current;

    public AlgorithmService(RuleState state, GenerateRegionUseCase region) {
        this.state = state;
        this.region = region;
        rebuild();
        state.addChangeListener(this::rebuild);
    }

    @Override
    public TerrainAlgorithm current() {
        return current;
    }

    /** Returns true if the given id maps to a known algorithm. */
    public static boolean isKnown(String algorithmId) {
        return switch (algorithmId) {
            case WfcSurfaceAlgorithm.ID, IndependentSkyGridAlgorithm.ID, NoiseSkyGridAlgorithm.ID -> true;
            default -> false;
        };
    }

    private void rebuild() {
        GeneratorConfig config = state.current();
        this.current = switch (config.algorithm()) {
            case IndependentSkyGridAlgorithm.ID -> new IndependentSkyGridAlgorithm(config.skygrid());
            case NoiseSkyGridAlgorithm.ID -> new NoiseSkyGridAlgorithm(config.skygrid());
            default -> new WfcSurfaceAlgorithm(region); // includes the explicit "wfc" id
        };
    }
}

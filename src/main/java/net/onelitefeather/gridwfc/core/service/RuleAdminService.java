package net.onelitefeather.gridwfc.core.service;

import net.onelitefeather.gridwfc.core.model.GeneratorConfig;
import net.onelitefeather.gridwfc.core.model.TileType;
import net.onelitefeather.gridwfc.core.port.in.RuleAdminUseCase;
import net.onelitefeather.gridwfc.core.port.out.RuleRepository;
import net.onelitefeather.gridwfc.core.state.RuleState;

import java.util.List;
import java.util.Optional;

/**
 * Implements in game administration. Validates input against the domain and delegates persistence
 * to the {@link RuleRepository} out port. Changes go through {@link RuleState}, which handles undo
 * (Memento) and cache invalidation plus algorithm rebuilds (Observer).
 */
public final class RuleAdminService implements RuleAdminUseCase {

    private final RuleState state;
    private final RuleRepository repository;

    public RuleAdminService(RuleState state, RuleRepository repository) {
        this.state = state;
        this.repository = repository;
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
        if (!AlgorithmService.isKnown(algorithmId)) {
            return false;
        }
        state.apply(state.current().withAlgorithm(algorithmId));
        return true;
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
        state.replace(repository.load());
    }

    @Override
    public void save() {
        repository.save(state.current());
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
        GeneratorConfig config = state.current();
        state.apply(config.withSkygrid(config.skygrid().withBlockWeight(material, weight)));
        return true;
    }

    @Override
    public boolean undo() {
        return state.undo();
    }
}

package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.SkyGridApi;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.TileType;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;

import java.util.List;
import java.util.Optional;

/**
 * Read only implementation of the public {@link SkyGridApi}, backed by the in port that already owns
 * the configuration snapshot. Keeping the behavior here leaves the api module free of implementation,
 * and the returned model is immutable, so callers cannot mutate generator state through it.
 */
public final class SkyGridApiService implements SkyGridApi {

    private final RuleAdminUseCase admin;

    public SkyGridApiService(RuleAdminUseCase admin) {
        this.admin = admin;
    }

    @Override
    public String algorithm() {
        return admin.algorithm();
    }

    @Override
    public GeneratorConfig configuration() {
        return admin.current();
    }

    @Override
    public List<String> tileIds() {
        return admin.tileIds();
    }

    @Override
    public Optional<TileType> tile(String id) {
        return admin.tile(id);
    }
}

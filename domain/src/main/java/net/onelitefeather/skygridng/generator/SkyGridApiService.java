package net.onelitefeather.skygridng.generator;

import net.kyori.adventure.text.Component;

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
    public Component describe() {
        // A translatable summary built here in the core, proving the platform-neutral layer can speak
        // Adventure directly: the platform's i18n renders the key per viewer locale.
        GeneratorConfig config = admin.current();
        return Component.translatable(
                "skygridng.api.summary",
                Component.text(admin.algorithm()),
                Component.text(config.skygrid().spacing()),
                Component.text(config.biomes().size()));
    }
}

package net.onelitefeather.skygridng.generator;

import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.api.model.Season;

import java.util.Optional;

/**
 * The public, stable entry point other plugins and OneLiteFeather network components use to inspect
 * a running SkyGrid-ng generator.
 *
 * On a Bukkit server an instance is registered with the {@code ServicesManager}, so consumers
 * obtain it with {@code getServer().getServicesManager().load(SkyGridApi.class)}. This interface and
 * the {@link net.onelitefeather.skygridng.api.model model} it returns are framework free and
 * semantically versioned. It exposes read only access, so callers cannot corrupt generator state.
 */
public interface SkyGridApi {

    /** Semantic version of this API surface, independent of the plugin version. */
    String API_VERSION = "2.0.0";

    /** Returns {@link #API_VERSION}. */
    default String apiVersion() {
        return API_VERSION;
    }

    /** The active generation algorithm id: {@code "skygrid"} or {@code "noise_skygrid"}. */
    String algorithm();

    /** An immutable snapshot of the current generator configuration. */
    GeneratorConfig configuration();

    /**
     * A localized, ready-to-display summary of the active generator (algorithm, grid spacing, biome
     * count) as an Adventure {@link Component}. It is a {@code translatable} component, so it
     * renders in the viewer's locale wherever the plugin's translations are registered. Consumers
     * (for example an OneLiteFeather network status display) can send it straight to any audience
     * without rebuilding the text themselves.
     */
    Component describe();

    /**
     * The season that is running now, or empty when no season is configured. It is read only, so other
     * plugins can key their own data by the season without the season lifecycle being exposed.
     */
    default Optional<Season> currentSeason() {
        return Optional.empty();
    }
}

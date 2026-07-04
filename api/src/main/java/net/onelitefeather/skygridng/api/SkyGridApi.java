package net.onelitefeather.skygridng.api;

import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.TileType;

import java.util.List;
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
    String API_VERSION = "1.0.0";

    /** Returns {@link #API_VERSION}. */
    default String apiVersion() {
        return API_VERSION;
    }

    /** The active generation algorithm id: {@code "skygrid"}, {@code "noise_skygrid"}, or {@code "wfc"}. */
    String algorithm();

    /** An immutable snapshot of the current generator configuration. */
    GeneratorConfig configuration();

    /** The ids of all configured tiles. */
    List<String> tileIds();

    /** The tile with the given id, or empty if none is configured. */
    Optional<TileType> tile(String id);

    /**
     * A localized, ready-to-display summary of the active generator (algorithm, tile count, sky
     * spacing) as an Adventure {@link Component}. It is a {@code translatable} component, so it
     * renders in the viewer's locale wherever the plugin's translations are registered. Consumers
     * (for example an OneLiteFeather network status display) can send it straight to any audience
     * without rebuilding the text themselves.
     */
    Component describe();
}

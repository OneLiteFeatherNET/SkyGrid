package net.onelitefeather.skygridng.platforms.common;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import net.onelitefeather.skygridng.adapters.command.SkyGridCommand;
import net.onelitefeather.skygridng.adapters.spawn.SpreadSpawnListener;
import net.onelitefeather.skygridng.platforms.common.injection.GeneratorModule;
import net.onelitefeather.skygridng.platforms.common.injection.PlatformModule;
import net.onelitefeather.skygridng.platforms.common.injection.RepositoryModule;
import net.onelitefeather.skygridng.platforms.olf.ChallengeModule;
import net.onelitefeather.skygridng.platforms.olf.OlfFeature;
import net.onelitefeather.skygridng.platforms.olf.OlfModule;
import net.onelitefeather.skygridng.api.SkyGridApi;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;
import net.onelitefeather.skygridng.core.state.RuleState;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Composition Root (Hexagonal): builds the Guice object graph and connects adapters to ports.
 *
 * The core knows none of these classes. Adapters and core services are wired in the injection
 * modules; the core itself carries no injection annotations and stays framework free. Swapping an
 * adapter changes a module binding, not the core.
 *
 * Dependency direction: adapters -> ports -> core. Never the other way around.
 *
 * This is the single, always fully wired plugin class: its default wiring already includes the
 * OLF feature modules ({@link OlfModule}, {@link ChallengeModule}), so there is no separate
 * distribution subclass. The extension seam ({@link #modules()}, {@link #injector()},
 * {@link #onExtensionEnable()}, {@link #onExtensionDisable()}) is kept for any future extension.
 *
 * Not final: MockBukkit v4 loads the plugin by subclassing it, so a final main class cannot be
 * tested.
 */
public class SkyGridNgPlugin extends JavaPlugin {

    private static final Key<Set<OlfFeature>> FEATURES = Key.get(new TypeLiteral<Set<OlfFeature>>() {});

    private final I18n i18n = new I18n();
    private Injector injector;
    private Set<OlfFeature> features;

    @Override
    public void onLoad() {
        // Eager singletons (Stage.PRODUCTION) build the whole graph here, including loading config.
        this.injector = Guice.createInjector(Stage.PRODUCTION, modules());
    }

    /**
     * The Guice modules that build the object graph. Kept overridable as an extension seam for any
     * future need, but the default body already performs the full wiring: the base generator modules
     * plus the OLF feature modules.
     */
    protected List<Module> modules() {
        List<Module> modules = new ArrayList<>();
        modules.add(new PlatformModule(this));
        modules.add(new RepositoryModule());
        modules.add(new GeneratorModule());
        modules.add(new OlfModule());
        modules.add(new ChallengeModule());
        return modules;
    }

    /** The composition root's injector, exposed for any future extension that wires extra features. */
    protected final Injector injector() {
        return injector;
    }

    @Override
    public void onEnable() {
        i18n.register();

        // Expose the read only public API so other plugins and OneLiteFeather network components
        // can inspect the running generator via the Bukkit ServicesManager.
        SkyGridApi api = injector.getInstance(SkyGridApi.class);
        getServer().getServicesManager().register(SkyGridApi.class, api, this, ServicePriority.Normal);

        String algorithm = injector.getInstance(RuleState.class).current().algorithm();
        if (!injector.getInstance(TerrainAlgorithms.class).isKnown(algorithm)) {
            getLogger().warning("Unknown algorithm '" + algorithm + "', falling back to skygrid.");
        }

        PluginCommand command = getCommand("skygrid");
        if (command != null) {
            SkyGridCommand executor = injector.getInstance(SkyGridCommand.class);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'skygrid' is not registered in plugin.yml.");
        }

        // Spread-spawn: first-join players are scattered onto their own platforms in our worlds.
        getServer().getPluginManager().registerEvents(injector.getInstance(SpreadSpawnListener.class), this);

        getLogger().info("SkyGrid-ng enabled. World generator: \"SkyGrid-ng\". Algorithm: " + algorithm + ".");

        onExtensionEnable();
    }

    @Override
    public void onDisable() {
        onExtensionDisable();
        i18n.unregister();
    }

    /**
     * Extension seam, kept for any future need. By default this enables every bound
     * {@link OlfFeature} after the base generator is wired and the public API is registered.
     */
    protected void onExtensionEnable() {
        features = injector().getInstance(FEATURES);
        for (OlfFeature feature : features) {
            feature.enable();
            getLogger().info("[OLF] Enabled feature: " + feature.id() + ".");
        }
    }

    /** Extension seam, kept for any future need. By default this disables every bound {@link OlfFeature}. */
    protected void onExtensionDisable() {
        if (features != null) {
            features.forEach(OlfFeature::disable);
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        // The id is the part after the colon in the generator spec (SkyGrid-ng:<preset>); it selects
        // which preset this world generates from. A blank or unknown id falls back to the default.
        return injector.getInstance(WorldGenerators.class).forWorld(worldName, id);
    }
}

package net.onelitefeather.skygridng.platforms.common;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import net.onelitefeather.skygridng.adapters.command.CloudCommandManagerFactory;
import net.onelitefeather.skygridng.adapters.command.SkyGridCommand;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import net.onelitefeather.skygridng.adapters.spawn.SpreadSpawnListener;
import net.onelitefeather.skygridng.platforms.common.injection.GeneratorModule;
import net.onelitefeather.skygridng.platforms.common.injection.PlatformModule;
import net.onelitefeather.skygridng.platforms.common.injection.RepositoryModule;
import net.onelitefeather.skygridng.platforms.olf.ChallengeModule;
import net.onelitefeather.skygridng.platforms.olf.OlfFeature;
import net.onelitefeather.skygridng.platforms.olf.OlfModule;
import net.onelitefeather.skygridng.api.SkyGridApi;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import net.onelitefeather.skygridng.core.state.RuleState;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.paper.PaperCommandManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

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
    private PaperCommandManager<CommandSource> cloudCommandManager;
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

        // Cloud command framework migration, stage 1: build the sender-mapped manager so it exists
        // side by side with the legacy executor above. Its constructor eagerly reflects into the
        // server's real Brigadier argument type registry; a test double (MockBukkit) or a server
        // fork without that registry throws a LinkageError (ExceptionInInitializerError or
        // NoClassDefFoundError) from a static initializer, not a plain exception. Guard it the same
        // way the missing "skygrid" registration above is guarded, so a construction failure never
        // takes down the rest of the plugin. Stage 2 registers the migrated subcommands here.
        try {
            cloudCommandManager = CloudCommandManagerFactory.create(this);
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.WARNING, "Failed to build the Cloud command manager.", e);
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

        // ChallengeProgressRepository's Postgres adapter lazily opens a HikariCP pool on first use;
        // whether or not it was ever used this run, ask the injector for the bound singleton (Guice
        // memoizes it, so this never reopens or duplicates anything) and close it if it is
        // AutoCloseable, so a reload or server stop never leaks pooled connections or Hikari's
        // housekeeping threads.
        if (injector.getInstance(ChallengeProgressRepository.class) instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to close the challenge progress repository.", e);
            }
        }
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

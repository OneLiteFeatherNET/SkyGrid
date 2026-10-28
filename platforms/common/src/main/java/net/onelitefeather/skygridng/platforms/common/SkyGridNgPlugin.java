package net.onelitefeather.skygridng.platforms.common;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import net.onelitefeather.skygridng.adapters.command.handler.CloudCommandHandler;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import net.onelitefeather.skygridng.platforms.common.command.CloudCommandManagerFactory;
import net.onelitefeather.skygridng.platforms.common.command.CloudCommandRegistrar;
import net.onelitefeather.skygridng.platforms.common.injection.CommandModule;
import net.onelitefeather.skygridng.platforms.common.injection.GeneratorModule;
import net.onelitefeather.skygridng.platforms.common.injection.PlatformModule;
import net.onelitefeather.skygridng.platforms.common.injection.RepositoryModule;
import net.onelitefeather.skygridng.platforms.common.spawn.SpreadSpawnListener;
import net.onelitefeather.skygridng.platforms.olf.ChallengeModule;
import net.onelitefeather.skygridng.platforms.olf.OlfFeature;
import net.onelitefeather.skygridng.platforms.olf.OlfModule;
import net.onelitefeather.skygridng.platforms.olf.season.SeasonModule;
import net.onelitefeather.skygridng.adapters.persistence.AbstractJpaRepository;
import net.onelitefeather.skygridng.api.SkyGridApi;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.SeasonAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import net.onelitefeather.skygridng.ports.out.SeasonRunRepository;
import net.onelitefeather.skygridng.ports.out.TaskCompletionRepository;
import net.onelitefeather.skygridng.core.state.RuleState;
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
    private static final Key<Set<CloudCommandHandler>> COMMAND_HANDLERS =
            Key.get(new TypeLiteral<Set<CloudCommandHandler>>() {});

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
        modules.add(new CommandModule());
        modules.add(new OlfModule());
        modules.add(new ChallengeModule());
        modules.add(new SeasonModule());
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

        // Cloud command framework migration, stage 2: build the sender-mapped manager and register
        // the migrated annotated subcommands (preset, presets, worlds, algorithm, skyweight,
        // reload, save, undo, challenge, help) on it. The manager's constructor eagerly reflects
        // into the server's real Brigadier argument type registry; a test double (MockBukkit) or a
        // server fork without that registry throws a LinkageError (ExceptionInInitializerError or
        // NoClassDefFoundError) from a static initializer, not a plain exception. Guard both steps
        // so a construction or registration failure never takes down the rest of the plugin. There
        // is no hand-rolled fallback executor anymore (stage 1's legacy getCommand("skygrid")
        // wiring is gone for good), so a failure here means no /skygrid command at all, logged
        // clearly instead of crashing enable.
        try {
            cloudCommandManager = CloudCommandManagerFactory.create(this);
            CloudCommandRegistrar.register(
                    cloudCommandManager,
                    injector.getInstance(Presets.class),
                    injector.getInstance(RuleAdminUseCase.class),
                    injector.getInstance(ChallengeAdminUseCase.class),
                    injector.getInstance(SeasonAdminUseCase.class),
                    injector.getInstance(COMMAND_HANDLERS));
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.WARNING, "Failed to build the Cloud command manager.", e);
        }

        // Spread-spawn: first-join players are scattered onto their own platforms in our worlds.
        getServer().getPluginManager().registerEvents(injector.getInstance(SpreadSpawnListener.class), this);

        warmUpDatabases();

        getLogger().info("SkyGrid-ng enabled. World generator: \"SkyGrid-ng\". Algorithm: " + algorithm + ".");

        onExtensionEnable();
    }

    /**
     * Runs the Flyway migrations and the Hibernate bootstrap of the Postgres backed repositories on an
     * async thread. Left to the first command or task completion, that blocking work ran on the server
     * main thread and tripped the watchdog. A failure is logged as one warning per database and never
     * disables the plugin: the repository keeps retrying lazily on its first real use.
     */
    private void warmUpDatabases() {
        List<Object> repositories = List.of(
                injector.getInstance(ChallengeProgressRepository.class),
                injector.getInstance(TaskCompletionRepository.class),
                injector.getInstance(SeasonRunRepository.class));
        DatabaseWarmUpGate gate = injector.getInstance(DatabaseWarmUpGate.class);
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                for (Object repository : repositories) {
                    if (repository instanceof AbstractJpaRepository database) {
                        try {
                            database.warmUp();
                        } catch (RuntimeException e) {
                            getLogger().log(Level.WARNING, "Database warm-up failed for "
                                    + database.getClass().getSimpleName() + ", it will retry on first use: "
                                    + e.getMessage(), e);
                        }
                    }
                }
            } finally {
                // The season ticker waits for this gate, whether the warm-up succeeded or failed.
                gate.markWarmedUp();
            }
        });
    }

    @Override
    public void onDisable() {
        onExtensionDisable();
        i18n.unregister();

        // The Postgres adapters lazily open a HikariCP pool on first use; whether or not they were ever used
        // this run, close the bound singletons (Guice memoizes them, so this never reopens or duplicates
        // anything), so a reload or server stop never leaks pooled connections or Hikari's housekeeping threads.
        for (Object repository : databaseRepositories()) {
            if (repository instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to close " + repository.getClass().getSimpleName() + ".", e);
                }
            }
        }
    }

    /** The database repositories this plugin owns and closes on disable. */
    List<Object> databaseRepositories() {
        return List.of(
                injector.getInstance(ChallengeProgressRepository.class),
                injector.getInstance(TaskCompletionRepository.class),
                injector.getInstance(SeasonRunRepository.class));
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

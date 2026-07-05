package net.onelitefeather.skygridng.platforms.common;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import net.onelitefeather.skygridng.adapters.command.SkyGridCommand;
import net.onelitefeather.skygridng.adapters.spawn.SpreadSpawnListener;
import net.onelitefeather.skygridng.platforms.common.injection.GeneratorModule;
import net.onelitefeather.skygridng.platforms.common.injection.PlatformModule;
import net.onelitefeather.skygridng.platforms.common.injection.RepositoryModule;
import net.onelitefeather.skygridng.api.SkyGridApi;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;
import net.onelitefeather.skygridng.core.state.RuleState;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Composition Root (Hexagonal): builds the Guice object graph and connects adapters to ports.
 *
 * The core knows none of these classes. Adapters and core services are wired in the injection
 * modules; the core itself carries no injection annotations and stays framework free. Swapping an
 * adapter changes a module binding, not the core.
 *
 * Dependency direction: adapters -> ports -> core. Never the other way around.
 *
 * Not final: MockBukkit v4 loads the plugin by subclassing it, so a final main class cannot be
 * tested. The OLF distribution reuses this class as its composition root as well.
 */
public class SkyGridNgPlugin extends JavaPlugin {

    private final I18n i18n = new I18n();
    private Injector injector;

    @Override
    public void onLoad() {
        // Eager singletons (Stage.PRODUCTION) build the whole graph here, including loading config.
        this.injector = Guice.createInjector(Stage.PRODUCTION, modules());
    }

    /**
     * The Guice modules that build the object graph. A platform distribution (for example the OLF
     * network) overrides this to layer additional modules on top of the base wiring, then calls
     * {@code super.modules()} and appends its own.
     */
    protected List<Module> modules() {
        List<Module> modules = new ArrayList<>();
        modules.add(new PlatformModule(this));
        modules.add(new RepositoryModule());
        modules.add(new GeneratorModule());
        return modules;
    }

    /** The composition root's injector, for distribution subclasses that wire extra features. */
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
     * Hook for distribution subclasses to enable their extra features after the base generator is
     * wired and the public API is registered. The base distribution adds nothing.
     */
    protected void onExtensionEnable() {
    }

    /** Hook for distribution subclasses to disable their extra features before shutdown. */
    protected void onExtensionDisable() {
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        // The id is the part after the colon in the generator spec (SkyGrid-ng:<preset>); it selects
        // which preset this world generates from. A blank or unknown id falls back to the default.
        return injector.getInstance(WorldGenerators.class).forWorld(worldName, id);
    }
}

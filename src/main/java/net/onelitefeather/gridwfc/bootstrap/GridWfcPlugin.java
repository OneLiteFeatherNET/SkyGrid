package net.onelitefeather.gridwfc.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import net.onelitefeather.gridwfc.adapter.in.command.WfcCommand;
import net.onelitefeather.gridwfc.adapter.in.paper.HexagonalChunkGenerator;
import net.onelitefeather.gridwfc.bootstrap.injection.GeneratorModule;
import net.onelitefeather.gridwfc.bootstrap.injection.PlatformModule;
import net.onelitefeather.gridwfc.bootstrap.injection.RepositoryModule;
import net.onelitefeather.gridwfc.core.service.AlgorithmService;
import net.onelitefeather.gridwfc.core.state.RuleState;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Composition Root (Hexagonal): builds the Guice object graph and connects adapters to ports.
 *
 * The core knows none of these classes. Adapters and core services are wired in the injection
 * modules; the core itself carries no injection annotations and stays framework free. Swapping an
 * adapter changes a module binding, not the core.
 *
 * Dependency direction: adapters -> ports -> core. Never the other way around.
 */
public final class GridWfcPlugin extends JavaPlugin {

    private final I18n i18n = new I18n();
    private Injector injector;

    @Override
    public void onLoad() {
        // Eager singletons (Stage.PRODUCTION) build the whole graph here, including loading config.
        this.injector = Guice.createInjector(
                Stage.PRODUCTION,
                new PlatformModule(this),
                new RepositoryModule(),
                new GeneratorModule());
    }

    @Override
    public void onEnable() {
        i18n.register();

        String algorithm = injector.getInstance(RuleState.class).current().algorithm();
        if (!AlgorithmService.isKnown(algorithm)) {
            getLogger().warning("Unknown algorithm '" + algorithm + "', falling back to WFC.");
        }

        PluginCommand command = getCommand("wfc");
        if (command != null) {
            WfcCommand executor = injector.getInstance(WfcCommand.class);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'wfc' is not registered in plugin.yml.");
        }

        getLogger().info("GridWFC enabled. World generator: \"GridWFC\". Algorithm: " + algorithm + ".");
    }

    @Override
    public void onDisable() {
        i18n.unregister();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return injector.getInstance(HexagonalChunkGenerator.class);
    }
}

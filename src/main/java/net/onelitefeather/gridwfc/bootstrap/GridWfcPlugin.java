package net.onelitefeather.gridwfc.bootstrap;

import net.onelitefeather.gridwfc.adapter.in.command.WfcCommand;
import net.onelitefeather.gridwfc.adapter.in.paper.HexagonalChunkGenerator;
import net.onelitefeather.gridwfc.adapter.out.json.JsonRuleRepository;
import net.onelitefeather.gridwfc.core.model.GeneratorConfig;
import net.onelitefeather.gridwfc.core.port.out.RuleRepository;
import net.onelitefeather.gridwfc.core.service.AlgorithmService;
import net.onelitefeather.gridwfc.core.service.RegionGenerationService;
import net.onelitefeather.gridwfc.core.service.RuleAdminService;
import net.onelitefeather.gridwfc.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.gridwfc.core.solver.WeightedRandomCollapse;
import net.onelitefeather.gridwfc.core.solver.WfcSolver;
import net.onelitefeather.gridwfc.core.state.RuleState;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Composition Root (Hexagonal): the only place where adapters are wired to ports.
 *
 * The core knows none of these classes directly. Swapping an adapter (for example the
 * RuleRepository from JSON to a database) changes only this file.
 *
 * Dependency direction: adapters -> ports -> core. Never the other way around.
 */
public final class GridWfcPlugin extends JavaPlugin {

    private final I18n i18n = new I18n();
    private RegionGenerationService regionService;
    private AlgorithmService algorithmService;

    @Override
    public void onEnable() {
        i18n.register();

        // Out adapter: JSON repository with the bundled default.
        Path configFile = getDataFolder().toPath().resolve("generator.json");
        RuleRepository repository = new JsonRuleRepository(configFile, defaultConfigJson());

        // Core: state + solver strategies + services.
        GeneratorConfig initial = repository.load();
        if (!AlgorithmService.isKnown(initial.algorithm())) {
            getLogger().warning("Unknown algorithm '" + initial.algorithm() + "', falling back to WFC.");
        }
        RuleState state = new RuleState(initial, 20);
        WfcSolver solver = new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse());

        this.regionService = new RegionGenerationService(state, solver);
        this.algorithmService = new AlgorithmService(state, regionService);
        RuleAdminService adminService = new RuleAdminService(state, repository);

        // In adapter: command.
        PluginCommand command = getCommand("wfc");
        if (command != null) {
            WfcCommand executor = new WfcCommand(adminService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'wfc' is not registered in plugin.yml.");
        }

        getLogger().info("GridWFC enabled. World generator: \"GridWFC\". Algorithm: "
                + initial.algorithm() + ". Config: " + configFile);
    }

    @Override
    public void onDisable() {
        i18n.unregister();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        // In adapter: Paper generator wired to the algorithm provider and the WFC region use case.
        return new HexagonalChunkGenerator(algorithmService, regionService);
    }

    private String defaultConfigJson() {
        try (InputStream in = getResource("generator.json")) {
            if (in == null) {
                throw new IllegalStateException("generator.json not found in plugin jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read bundled generator.json", e);
        }
    }
}

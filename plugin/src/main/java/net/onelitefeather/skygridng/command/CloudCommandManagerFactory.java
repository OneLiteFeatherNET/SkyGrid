package net.onelitefeather.skygridng.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

/**
 * Builds the Incendo Cloud command manager, mapped to the platform-neutral {@link CommandSource}
 * so that subcommand handlers ({@code command.SkyGridCommands}) never see a Paper
 * {@code CommandSourceStack} directly.
 */
public final class CloudCommandManagerFactory {

    private CloudCommandManagerFactory() {
    }

    public static PaperCommandManager<CommandSource> create(JavaPlugin plugin) {
        SenderMapper<CommandSourceStack, CommandSource> senderMapper = SenderMapper.create(
                PaperCommandSource::new,
                source -> ((PaperCommandSource) source).stack());
        return PaperCommandManager.builder(senderMapper)
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(plugin);
    }
}

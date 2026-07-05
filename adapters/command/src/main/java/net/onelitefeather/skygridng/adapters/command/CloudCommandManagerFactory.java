package net.onelitefeather.skygridng.adapters.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

/**
 * Builds the Incendo Cloud command manager, mapped to the platform-neutral {@link CommandSource}
 * so that subcommand handlers (migration stage 2) never see a Paper {@code CommandSourceStack}
 * directly. Stage 1 only constructs the manager; no command is registered on it yet.
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

package net.onelitefeather.skygridng.platforms.common.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The Paper binding of {@link CommandSource}: forwards feedback to the underlying
 * {@link CommandSourceStack}'s sender. This is the only place stage 2's Bukkit-free subcommand
 * logic touches a real Paper sender.
 */
public record PaperCommandSource(CommandSourceStack stack) implements CommandSource {

    public PaperCommandSource {
        Objects.requireNonNull(stack, "stack");
    }

    @Override
    public void sendMessage(Component message) {
        stack.getSender().sendMessage(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return stack.getSender().hasPermission(permission);
    }

    @Override
    public Optional<UUID> playerId() {
        return stack.getSender() instanceof Player player
                ? Optional.of(player.getUniqueId())
                : Optional.empty();
    }

    @Override
    public Optional<UUID> resolvePlayerId(String name) {
        // Bukkit.getOfflinePlayer(String) resolves an online player instantly; for a name never
        // seen before it may block on a Mojang UUID lookup. Accepted here since this is a rarely
        // used admin command, not a hot path.
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        return Optional.of(target.getUniqueId());
    }
}

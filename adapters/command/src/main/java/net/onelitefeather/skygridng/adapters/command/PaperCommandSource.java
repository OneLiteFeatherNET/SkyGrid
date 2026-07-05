package net.onelitefeather.skygridng.adapters.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;

import java.util.Objects;

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
}

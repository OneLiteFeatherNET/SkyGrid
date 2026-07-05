package net.onelitefeather.skygridng.adapters.command.handler;

import net.kyori.adventure.text.Component;

/**
 * A neutral receiver of command feedback, independent of Bukkit or Paper, so that subcommand
 * handler logic (added in migration stage 2) can compile without importing Bukkit or cloud's
 * Paper/Brigadier bindings.
 */
public interface CommandSource {

    void sendMessage(Component message);

    boolean hasPermission(String permission);
}

package net.onelitefeather.skygridng.command;

import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A neutral receiver of command feedback, independent of Bukkit or Paper, so that subcommand
 * handler logic (added in migration stage 2) can compile without importing Bukkit or cloud's
 * Paper/Brigadier bindings.
 */
public interface CommandSource {

    void sendMessage(Component message);

    boolean hasPermission(String permission);

    /** The sender's own id, empty if the sender is not a player (for example the console). */
    Optional<UUID> playerId();

    /**
     * Resolves a player name to an id, independent of whether that player is currently online.
     * Empty if the name is not known to have ever joined this server.
     */
    Optional<UUID> resolvePlayerId(String name);

    /** The locale the sender reads, used to render absolute dates; the console reads English. */
    Locale locale();
}

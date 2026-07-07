package net.onelitefeather.skygridng.adapters.challenge;

import jakarta.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.onelitefeather.skygridng.api.model.ItemId;
import net.onelitefeather.skygridng.api.model.Reward;
import net.onelitefeather.skygridng.ports.out.RewardGrantor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Driven adapter: grants a config-authored {@link Reward} to a player, resolved via an exhaustive
 * {@code switch} over {@link Reward}'s three permitted record shapes with no {@code default}
 * branch, so a fourth {@link Reward} variant added later fails to compile here instead of silently
 * granting nothing.
 *
 * All three variants are best-effort for an offline player: this is config-authored, low-stakes
 * quest content (not a production mail system), so a reward for a player who is not currently
 * online is dropped with a logged warning ({@link Reward.ItemGrant}, {@link Reward.PlayerMessage})
 * or degrades gracefully ({@link Reward.CommandDispatch} substitutes the raw {@link UUID} string
 * instead of a name), rather than queuing anything for later delivery.
 */
public final class RewardGrantorImpl implements RewardGrantor {

    /**
     * A deliberately obvious placeholder for an unresolvable {@link net.onelitefeather.skygridng.api.model.ItemId}:
     * unlike a plausible-looking block such as {@code Material.STONE}, a barrier in a player's
     * inventory is immediately recognizable as "this reward config is broken" rather than silently
     * passing as a legitimate, if boring, item reward.
     */
    private static final Material FALLBACK_ITEM = Material.BARRIER;

    private static final String PLAYER_PLACEHOLDER = "{player}";

    private final JavaPlugin plugin;
    private final ItemResolver itemResolver;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Inject
    public RewardGrantorImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemResolver = new ItemResolver(FALLBACK_ITEM);
    }

    @Override
    public void grant(UUID playerId, Reward reward) {
        switch (reward) {
            case Reward.ItemGrant(var item, var quantity) -> grantItem(playerId, item, quantity);
            case Reward.CommandDispatch(var command) -> dispatchCommand(playerId, command);
            case Reward.PlayerMessage(var text) -> sendPlayerMessage(playerId, text);
        }
    }

    private void grantItem(UUID playerId, ItemId item, int quantity) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            plugin.getLogger().warning(
                    "Dropped an ItemGrant reward (" + item.asString() + " x" + quantity
                            + ") for offline player " + playerId);
            return;
        }
        Material material = itemResolver.resolve(item);
        player.getInventory().addItem(new ItemStack(material, quantity));
    }

    private void dispatchCommand(UUID playerId, String command) {
        Player player = plugin.getServer().getPlayer(playerId);
        String replacement = player != null ? player.getName() : playerId.toString();
        String substituted = substitutePlaceholder(command, replacement);
        // Command dispatch touches the world/command map and must run on the main thread; the rest
        // of this module schedules main-thread work the same way (see LevelBorderWarningTask).
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), substituted));
    }

    private void sendPlayerMessage(UUID playerId, String text) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            plugin.getLogger().warning("Dropped a PlayerMessage reward for offline player " + playerId);
            return;
        }
        Component message = miniMessage.deserialize(text);
        player.sendMessage(message);
    }

    /** The {@code {player}} substitution in isolation, unit-testable without a Bukkit dispatch. */
    static String substitutePlaceholder(String command, String replacement) {
        return command.replace(PLAYER_PLACEHOLDER, replacement);
    }
}

package net.onelitefeather.skygridng.season;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * The Bukkit implementation of {@link PlayerSeasonReset}: clears the vanilla state of a player on the first entry into
 * a season. The season id is stored under {@link #MARKER}, so a second entry into the same season does nothing. An
 * offline player is skipped; the join listener resets that player when it joins.
 */
public final class BukkitPlayerSeasonReset implements PlayerSeasonReset {

    static final NamespacedKey MARKER = new NamespacedKey("skygridng", "season");

    private static final double FULL_HEALTH = 20d;

    private final Plugin plugin;

    public BukkitPlayerSeasonReset(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void reset(UUID playerId, SeasonId season) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        MainThread.run(plugin, () -> resetOnMainThread(player, season));
    }

    private static void resetOnMainThread(Player player, SeasonId season) {
        if (season.value().equals(player.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))) {
            return;
        }
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0f);
        player.setTotalExperience(0);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? FULL_HEALTH : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setRespawnLocation(null, true);
        player.setGameMode(GameMode.SURVIVAL);
        player.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, season.value());
    }
}

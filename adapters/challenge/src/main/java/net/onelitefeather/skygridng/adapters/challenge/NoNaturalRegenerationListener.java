package net.onelitefeather.skygridng.adapters.challenge;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Predicate;

/**
 * Driving adapter: enforces the {@code no_natural_regeneration} challenge by cancelling only the
 * "full hunger bar" health regain ({@link EntityRegainHealthEvent.RegainReason#SATIATED}) for a
 * player, while the challenge is active in a SkyGrid-ng world.
 *
 * This deliberately leaves every other regain reason untouched (golden apples, potions, magic,
 * and so on keep healing normally), and it deliberately does not touch
 * {@code FoodLevelChangeEvent}: hunger still depletes exactly as vanilla, only the passive
 * heal-from-full-hunger effect is removed.
 */
public final class NoNaturalRegenerationListener implements Listener, ChallengeBehavior {

    static final String CHALLENGE_ID = "no_natural_regeneration";

    private final ChallengeAdminUseCase challenges;
    private final Predicate<World> appliesTo;
    private final JavaPlugin plugin;

    @Inject
    public NoNaturalRegenerationListener(ChallengeAdminUseCase challenges, Predicate<World> appliesTo, JavaPlugin plugin) {
        this.challenges = challenges;
        this.appliesTo = appliesTo;
        this.plugin = plugin;
    }

    @Override
    public String challengeId() {
        return CHALLENGE_ID;
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) {
            return;
        }
        if (!challenges.isActive(CHALLENGE_ID) || !appliesTo.test(player.getWorld())) {
            return;
        }
        event.setCancelled(true);
    }
}

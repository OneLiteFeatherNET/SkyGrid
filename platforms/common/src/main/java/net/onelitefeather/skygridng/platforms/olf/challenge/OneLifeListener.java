package net.onelitefeather.skygridng.platforms.olf.challenge;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.challenge.ChallengeBehavior;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Driving adapter: enforces the {@code one_life} challenge. A death while the challenge is active,
 * in a SkyGrid-ng world, permanently eliminates the player for the rest of the current server run:
 * their {@link UUID} is recorded in an in-memory set, and every respawn or later join forces
 * {@link GameMode#SPECTATOR}.
 *
 * The two halves of this listener are deliberately asymmetric. Recording an elimination
 * ({@link #onDeath}) is gated on both {@code appliesTo} and {@link ChallengeAdminUseCase#isActive}
 * at the moment of death, exactly like {@link NoCraftingTableListener}. Enforcing it
 * ({@link #onRespawn}, {@link #onJoin}) is NOT gated on either: once a player is eliminated, that
 * fact must survive a relogin and must not be undone by the challenge later being deactivated or by
 * the player moving to a different world, because "you are out for this run" is a property of the
 * player, not of the current world or the current admin toggle.
 *
 * The eliminated set is server-run-scoped only: it lives in memory, is never pruned, and resets on
 * server restart. There is no persistence store for it yet; that is an accepted tradeoff at this
 * scale (see Epic I2's backlog status for a future season-persistence layer).
 */
public final class OneLifeListener implements Listener, ChallengeBehavior {

    static final String CHALLENGE_ID = "one_life";

    private final ChallengeAdminUseCase challenges;
    private final Predicate<World> appliesTo;
    private final JavaPlugin plugin;
    private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();

    @Inject
    public OneLifeListener(ChallengeAdminUseCase challenges, Predicate<World> appliesTo, JavaPlugin plugin) {
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
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!appliesTo.test(player.getWorld())) {
            return;
        }
        if (!challenges.isActive(CHALLENGE_ID)) {
            return;
        }
        eliminated.add(player.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (eliminated.contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (eliminated.contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }
}

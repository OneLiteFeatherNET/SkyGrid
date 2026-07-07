package net.onelitefeather.skygridng.adapters.challenge;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.TaskProgressUseCase;
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
 *
 * The round-boundary half follows {@link OneLifeListener}'s shape, minus any violation tracking: on
 * the challenge's active-to-inactive transition ({@link ChallengeAdminUseCase#addChangeListener}),
 * every online player in a matching world completes the {@code endured_the_round} task via {@link
 * TaskProgressUseCase#completeTask}, unconditionally. This is an "online at round end" attendance
 * bar rather than an achievement bar (no tracking of whether the player actually avoided
 * regeneration all round), an accepted skeleton for this slice.
 */
public final class NoNaturalRegenerationListener implements Listener, ChallengeBehavior {

    static final String CHALLENGE_ID = "no_natural_regeneration";

    private static final String TASK_ID = "endured_the_round";

    private final ChallengeAdminUseCase challenges;
    private final TaskProgressUseCase taskProgress;
    private final Predicate<World> appliesTo;
    private final JavaPlugin plugin;

    /**
     * The challenge's active state as last observed by {@link #onCatalogChanged()}. See {@link
     * OneLifeListener#lastActive} for the full rationale; the same pattern is reused here.
     */
    private volatile boolean lastActive;

    @Inject
    public NoNaturalRegenerationListener(ChallengeAdminUseCase challenges, TaskProgressUseCase taskProgress,
                                          Predicate<World> appliesTo, JavaPlugin plugin) {
        this.challenges = challenges;
        this.taskProgress = taskProgress;
        this.appliesTo = appliesTo;
        this.plugin = plugin;
        this.lastActive = challenges.isActive(CHALLENGE_ID);
    }

    @Override
    public String challengeId() {
        return CHALLENGE_ID;
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        challenges.addChangeListener(this::onCatalogChanged);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    /**
     * Reacts to any catalog change by comparing the challenge's current active state against
     * {@link #lastActive}. Only an active-to-inactive transition ends a round; every other
     * transition just updates the remembered state. See {@link OneLifeListener#onCatalogChanged()}.
     */
    private void onCatalogChanged() {
        boolean isActiveNow = challenges.isActive(CHALLENGE_ID);
        if (lastActive && !isActiveNow) {
            completeRound();
        }
        lastActive = isActiveNow;
    }

    /**
     * Ends a {@code no_natural_regeneration} round: every online player in a matching world
     * completes {@code endured_the_round}, regardless of anything else.
     */
    private void completeRound() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!appliesTo.test(player.getWorld())) {
                continue;
            }
            taskProgress.completeTask(player.getUniqueId(), CHALLENGE_ID, TASK_ID);
        }
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

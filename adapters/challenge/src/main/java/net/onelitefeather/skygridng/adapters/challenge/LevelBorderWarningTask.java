package net.onelitefeather.skygridng.adapters.challenge;

import jakarta.inject.Inject;
import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Driving adapter: periodic reminder for the {@code level_is_border} challenge. This is not a
 * {@link org.bukkit.event.Listener}; it is a plain {@link Runnable}. As a {@link ChallengeBehavior}
 * it self-schedules with {@code JavaPlugin.getServer().getScheduler().runTaskTimer(...)} on
 * {@link #enable()} and cancels the stored {@link BukkitTask} on {@link #disable()}; the
 * {@link Player} collection it reminds each tick is read via {@code plugin.getServer()} rather than a
 * separately injected {@link org.bukkit.Server}, since the plugin reference is needed anyway to
 * schedule and cancel the task and keeping a single field avoids the two ever drifting apart.
 *
 * The allowed radius is display only: a fixed {@value #BLOCKS_PER_LEVEL} blocks per experience
 * level, computed from the highest level among online players in a matching world. There is no real
 * {@link org.bukkit.WorldBorder} involved, this only sends an actionbar reminder; enforcing the
 * border is left to server operators or a future slice. The factor is a deliberate hardcoded
 * constant in this slice, not configurable.
 */
public final class LevelBorderWarningTask implements Runnable, ChallengeBehavior {

    static final String CHALLENGE_ID = "level_is_border";

    private static final long PERIOD_TICKS = 100L;

    /** Deliberate, hardcoded "display only" simplification: not configurable in this slice. */
    static final int BLOCKS_PER_LEVEL = 10;

    private final ChallengeAdminUseCase challenges;
    private final Predicate<World> appliesTo;
    private final JavaPlugin plugin;

    private BukkitTask task;

    @Inject
    public LevelBorderWarningTask(ChallengeAdminUseCase challenges, Predicate<World> appliesTo, JavaPlugin plugin) {
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
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 0L, PERIOD_TICKS);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void run() {
        if (!challenges.isActive(CHALLENGE_ID)) {
            return;
        }
        Collection<? extends Player> matching = plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> appliesTo.test(player.getWorld()))
                .toList();
        if (matching.isEmpty()) {
            return;
        }
        int highestLevel = matching.stream().mapToInt(Player::getLevel).max().orElse(0);
        int radius = radiusFor(highestLevel);
        Component message = Component.translatable(
                "skygridng.challenge.level_is_border.actionbar", Component.text(radius));
        for (Player player : matching) {
            player.sendActionBar(message);
        }
    }

    /**
     * The radius formula in isolation, unit-testable without sending any message:
     * {@code highestLevel * BLOCKS_PER_LEVEL} blocks.
     */
    static int radiusFor(int highestLevel) {
        return highestLevel * BLOCKS_PER_LEVEL;
    }
}

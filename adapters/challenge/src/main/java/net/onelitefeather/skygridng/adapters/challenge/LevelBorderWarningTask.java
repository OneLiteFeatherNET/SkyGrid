package net.onelitefeather.skygridng.adapters.challenge;

import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Driving adapter: periodic reminder for the {@code level_is_border} challenge. This is not a
 * {@link org.bukkit.event.Listener}; it is a plain {@link Runnable} meant to be registered with
 * {@code Bukkit.getScheduler().runTaskTimer(plugin, task, 0L, ticks)} by the OLF composition root,
 * not to self-schedule.
 *
 * The allowed radius is display only: a fixed {@value #BLOCKS_PER_LEVEL} blocks per experience
 * level, computed from the highest level among online players in a matching world. There is no real
 * {@link org.bukkit.WorldBorder} involved, this only sends an actionbar reminder; enforcing the
 * border is left to server operators or a future slice. The factor is a deliberate hardcoded
 * constant in this slice, not configurable.
 */
public final class LevelBorderWarningTask implements Runnable {

    static final String CHALLENGE_ID = "level_is_border";

    /** Deliberate, hardcoded "display only" simplification: not configurable in this slice. */
    static final int BLOCKS_PER_LEVEL = 10;

    private final ChallengeAdminUseCase challenges;
    private final Predicate<World> appliesTo;
    private final Server server;

    public LevelBorderWarningTask(ChallengeAdminUseCase challenges, Predicate<World> appliesTo, Server server) {
        this.challenges = challenges;
        this.appliesTo = appliesTo;
        this.server = server;
    }

    @Override
    public void run() {
        if (!challenges.isActive(CHALLENGE_ID)) {
            return;
        }
        Collection<? extends Player> matching = server.getOnlinePlayers().stream()
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

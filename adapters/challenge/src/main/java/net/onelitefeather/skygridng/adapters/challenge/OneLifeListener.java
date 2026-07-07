package net.onelitefeather.skygridng.adapters.challenge;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.ports.in.TaskProgressUseCase;
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
 * server restart. That part is unchanged and remains an accepted tradeoff at this scale. What this
 * closes is the persistence gap on the other side of the challenge: every {@code one_life} round
 * ends when the challenge transitions from active to inactive
 * ({@link ChallengeAdminUseCase#addChangeListener}), and at that moment every surviving player in a
 * matching world (present in {@code appliesTo}, absent from the eliminated set) has their tier
 * recorded through {@link ChallengeProgressUseCase#recordCompletion}, which is backed by durable
 * storage and therefore does survive a server restart, and also completes the quest-facing
 * {@code survive_the_round} task through {@link TaskProgressUseCase#completeTask}. The eliminated
 * set is then cleared for the next round.
 *
 * <p><b>One chat line, not two:</b> {@link TaskProgressUseCase#completeTask} grants the task's
 * configured reward exactly once, and {@code one_life.yml}'s {@code survive_the_round} task is
 * expected to carry a {@code PlayerMessage} reward ("You survived the round!"), delivered by the
 * reward-granting adapter. That would double up with this class's own previous direct chat message
 * announcing the earned tier, so {@link #completeRound()} no longer sends one itself: the
 * reward-driven message is now the sole round-completion chat line a survivor sees. The tier number
 * itself is not lost, it is still recorded through {@link ChallengeProgressUseCase#recordCompletion}
 * for whatever else reads it (a scoreboard, a future leaderboard); it is simply no longer announced
 * in chat by this class, only "you survived".
 */
public final class OneLifeListener implements Listener, ChallengeBehavior {

    static final String CHALLENGE_ID = "one_life";

    private static final String SURVIVE_TASK_ID = "survive_the_round";

    private final ChallengeAdminUseCase challenges;
    private final ChallengeProgressUseCase progress;
    private final TaskProgressUseCase taskProgress;
    private final Predicate<World> appliesTo;
    private final JavaPlugin plugin;
    private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();

    /**
     * The challenge's active state as last observed by {@link #onCatalogChanged()}, so that a bare
     * "something changed" notification can be turned into an active-to-inactive transition. Set at
     * construction time and updated on every notification; {@code volatile} because
     * {@link ChallengeAdminUseCase#addChangeListener} does not document which thread invokes the
     * listener.
     */
    private volatile boolean lastActive;

    @Inject
    public OneLifeListener(ChallengeAdminUseCase challenges, ChallengeProgressUseCase progress,
                            TaskProgressUseCase taskProgress, Predicate<World> appliesTo, JavaPlugin plugin) {
        this.challenges = challenges;
        this.progress = progress;
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
     * transition (inactive-to-active, or a change to some other challenge) just updates the
     * remembered state.
     */
    private void onCatalogChanged() {
        boolean isActiveNow = challenges.isActive(CHALLENGE_ID);
        if (lastActive && !isActiveNow) {
            completeRound();
        }
        lastActive = isActiveNow;
    }

    /**
     * Ends a {@code one_life} round: every online player in a matching world who was not eliminated
     * advances one tier (recorded, not announced here, see the class doc for why) and completes the
     * {@code survive_the_round} task, then the eliminated set is cleared for the next round,
     * regardless of whether any player advanced.
     */
    private void completeRound() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!appliesTo.test(player.getWorld()) || eliminated.contains(player.getUniqueId())) {
                continue;
            }
            progress.recordCompletion(player.getUniqueId(), CHALLENGE_ID);
            taskProgress.completeTask(player.getUniqueId(), CHALLENGE_ID, SURVIVE_TASK_ID);
        }
        eliminated.clear();
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

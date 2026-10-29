package net.onelitefeather.skygridng.challenge;

import jakarta.inject.Inject;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Driving adapter: enforces the {@code no_crafting_table} challenge by denying a crafting table's
 * own right-click behavior (opening its UI) while the challenge is active, in a SkyGrid-ng world.
 *
 * Only {@link EquipmentSlot#HAND} is handled: {@link PlayerInteractEvent} fires twice per physical
 * click (once for the main hand, once for the off hand), so acting on both would double-handle the
 * same click and could interfere with off-hand item use. {@link Event.Result#DENY} is applied via
 * {@link PlayerInteractEvent#setUseInteractedBlock(Event.Result)}, not {@code setCancelled(true)}:
 * cancelling the whole event would also block an unrelated hand-item use in the same physical
 * interaction (for example placing a block on top of the table), whereas denying only the
 * interacted block leaves everything else about the event alone. This covers both player-placed
 * and SkyGrid-generated floating-grid crafting tables uniformly, since Bukkit does not distinguish
 * block origin.
 *
 * The round-boundary half of this listener follows {@link OneLifeListener}'s shape: every player who
 * ever had a crafting table interaction denied this round is recorded in {@link #violatedThisRound},
 * and on the challenge's active-to-inactive transition ({@link ChallengeAdminUseCase#addChangeListener}),
 * every online player in a matching world who is NOT in that set has the {@code
 * no_crafting_table_round} task marked complete via {@link TaskProgressUseCase#completeTask}. The
 * set is cleared at the end of the round regardless of whether anyone completed the task.
 */
public final class NoCraftingTableListener implements Listener, ChallengeBehavior {

    static final String CHALLENGE_ID = "no_crafting_table";

    private static final String TASK_ID = "no_crafting_table_round";

    private final ChallengeAdminUseCase challenges;
    private final TaskProgressUseCase taskProgress;
    private final Predicate<World> appliesTo;
    private final JavaPlugin plugin;
    private final Set<UUID> violatedThisRound = ConcurrentHashMap.newKeySet();

    /**
     * The challenge's active state as last observed by {@link #onCatalogChanged()}. See {@link
     * OneLifeListener#lastActive} for the full rationale; the same pattern is reused here.
     */
    private volatile boolean lastActive;

    @Inject
    public NoCraftingTableListener(ChallengeAdminUseCase challenges, TaskProgressUseCase taskProgress,
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
     * Ends a {@code no_crafting_table} round: every online player in a matching world who never had
     * an interaction denied this round completes {@code no_crafting_table_round}, then {@link
     * #violatedThisRound} is cleared for the next round, regardless of whether any player completed
     * the task.
     */
    private void completeRound() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!appliesTo.test(player.getWorld()) || violatedThisRound.contains(player.getUniqueId())) {
                continue;
            }
            taskProgress.completeTask(player.getUniqueId(), CHALLENGE_ID, TASK_ID);
        }
        violatedThisRound.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CRAFTING_TABLE) {
            return;
        }
        if (!appliesTo.test(block.getWorld())) {
            return;
        }
        if (!challenges.isActive(CHALLENGE_ID)) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        violatedThisRound.add(event.getPlayer().getUniqueId());
    }
}

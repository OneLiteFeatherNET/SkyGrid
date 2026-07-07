package net.onelitefeather.skygridng.platforms.olf.challenge;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.challenge.ChallengeBehavior;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

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
 */
public final class NoCraftingTableListener implements Listener, ChallengeBehavior {

    static final String CHALLENGE_ID = "no_crafting_table";

    private final ChallengeAdminUseCase challenges;
    private final Predicate<World> appliesTo;
    private final JavaPlugin plugin;

    @Inject
    public NoCraftingTableListener(ChallengeAdminUseCase challenges, Predicate<World> appliesTo, JavaPlugin plugin) {
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
    }
}

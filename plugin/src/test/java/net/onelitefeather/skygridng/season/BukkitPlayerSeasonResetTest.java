package net.onelitefeather.skygridng.season;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.assertj.core.api.Assertions.assertThat;

/** Black-box tests for the once per season vanilla reset, marked in the player's persistent data. */
class BukkitPlayerSeasonResetTest {

    private static final SeasonId SEASON = new SeasonId("s01");

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void firstResetClearsTheInventoryAndMarksThePlayerForTheSeason() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.STONE));

        new BukkitPlayerSeasonReset(plugin).reset(player.getUniqueId(), SEASON);

        assertThat(player.getInventory().isEmpty())
                .as("the first entry into a season clears the vanilla inventory")
                .isTrue();
        assertThat(player.getPersistentDataContainer().get(BukkitPlayerSeasonReset.MARKER,
                org.bukkit.persistence.PersistentDataType.STRING))
                .as("the player carries the marker of the season it was reset for")
                .isEqualTo("s01");
    }

    @Test
    void secondResetForTheSameSeasonKeepsWhatThePlayerHasSince() {
        PlayerMock player = server.addPlayer();
        var reset = new BukkitPlayerSeasonReset(plugin);
        reset.reset(player.getUniqueId(), SEASON);
        player.getInventory().addItem(new ItemStack(Material.STONE));

        reset.reset(player.getUniqueId(), SEASON);

        assertThat(player.getInventory().contains(Material.STONE))
                .as("a second entry into the same season must not reset the player again")
                .isTrue();
    }
}

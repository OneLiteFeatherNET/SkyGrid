package net.onelitefeather.skygridng.platforms.olf.challenge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.onelitefeather.skygridng.api.model.ItemId;
import net.onelitefeather.skygridng.api.model.Reward;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockBukkit tests for the reward-granting driven adapter: an exhaustive switch over {@link Reward}
 * with one test group per permitted record shape.
 *
 * {@link Reward.CommandDispatch} is verified by registering a hand-rolled {@link Command} in the
 * server's command map that records the {@link CommandSender} and arguments it was invoked with,
 * then advancing the scheduler by one tick ({@link RewardGrantorImpl} schedules the dispatch onto
 * the main thread the same way {@link LevelBorderWarningTask} schedules its repeating reminder), so
 * the {@code {player}} substitution and the "runs as console" contract are both observed end to end.
 * The substitution itself is additionally unit-tested directly through the package-private
 * {@link RewardGrantorImpl#substitutePlaceholder(String, String)} helper.
 */
class RewardGrantorImplTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private RewardGrantorImpl grantor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
        grantor = new RewardGrantorImpl(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A hand-rolled command that records how it was last invoked, for {@code CommandDispatch} tests. */
    private static final class RecordingCommand extends Command {

        private CommandSender lastSender;
        private String[] lastArgs;

        private RecordingCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            this.lastSender = sender;
            this.lastArgs = args;
            return true;
        }
    }

    // ----- ItemGrant -----

    @Test
    @DisplayName("an ItemGrant reward adds the resolved item and quantity to an online player's inventory")
    void itemGrantAddsItemToOnlinePlayerInventory() {
        PlayerMock player = server.addPlayer();

        grantor.grant(player.getUniqueId(), new Reward.ItemGrant(ItemId.of("minecraft:diamond"), 3));

        assertThat(player.getInventory().all(Material.DIAMOND).values().stream()
                .mapToInt(item -> item.getAmount()).sum()).isEqualTo(3);
    }

    @Test
    @DisplayName("an ItemGrant reward for an offline player is dropped without throwing")
    void itemGrantForOfflinePlayerIsDroppedSilently() {
        UUID offlinePlayerId = UUID.randomUUID();

        grantor.grant(offlinePlayerId, new Reward.ItemGrant(ItemId.of("minecraft:diamond"), 1));
        // no assertion beyond "did not throw": there is no online player to inspect an inventory on.
    }

    // ----- CommandDispatch -----

    @Test
    @DisplayName("substitutePlaceholder replaces every occurrence of {player} with the given replacement")
    void substitutePlaceholderReplacesToken() {
        assertThat(RewardGrantorImpl.substitutePlaceholder("give {player} diamond 1", "Steve"))
                .isEqualTo("give Steve diamond 1");
    }

    @Test
    @DisplayName("a CommandDispatch reward substitutes the online player's name and runs through the console sender")
    void commandDispatchSubstitutesNameAndRunsAsConsole() {
        PlayerMock player = server.addPlayer("Steve");
        RecordingCommand command = new RecordingCommand("give");
        server.getCommandMap().register("test", command);

        grantor.grant(player.getUniqueId(), new Reward.CommandDispatch("give {player} diamond 1"));
        server.getScheduler().performOneTick();

        assertThat(command.lastSender).isEqualTo(server.getConsoleSender());
        assertThat(command.lastArgs).containsExactly("Steve", "diamond", "1");
    }

    @Test
    @DisplayName("a CommandDispatch reward for an offline player substitutes the raw UUID string instead of a name")
    void commandDispatchForOfflinePlayerUsesRawUuid() {
        UUID offlinePlayerId = UUID.randomUUID();
        RecordingCommand command = new RecordingCommand("give");
        server.getCommandMap().register("test", command);

        grantor.grant(offlinePlayerId, new Reward.CommandDispatch("give {player} diamond 1"));
        server.getScheduler().performOneTick();

        assertThat(command.lastArgs).containsExactly(offlinePlayerId.toString(), "diamond", "1");
    }

    // ----- PlayerMessage -----

    @Test
    @DisplayName("a PlayerMessage reward sends the parsed MiniMessage component to an online player")
    void playerMessageSendsParsedMiniMessageToOnlinePlayer() {
        PlayerMock player = server.addPlayer();

        grantor.grant(player.getUniqueId(), new Reward.PlayerMessage("<green>You survived the round!"));

        Component expected = MiniMessage.miniMessage().deserialize("<green>You survived the round!");
        assertThat(player.nextComponentMessage()).isEqualTo(expected);
    }

    @Test
    @DisplayName("a PlayerMessage reward for an offline player is dropped without throwing")
    void playerMessageForOfflinePlayerIsDroppedSilently() {
        UUID offlinePlayerId = UUID.randomUUID();

        grantor.grant(offlinePlayerId, new Reward.PlayerMessage("<green>You survived the round!"));
        // no assertion beyond "did not throw": there is no online player to receive a message.
    }
}

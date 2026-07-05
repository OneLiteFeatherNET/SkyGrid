package net.onelitefeather.skygridng.adapters.challenge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MockBukkit + Mockito tests for the driving adapter. {@link LevelBorderWarningTask#radiusFor(int)}
 * is a pure function and is unit-tested directly. {@code run()} is exercised through MockBukkit's
 * actionbar queue on {@link PlayerMock}: {@code nextActionBar()} polls the queue and returns
 * {@code null} when nothing was sent, which is all that is asserted about "no message sent". Where a
 * message is expected, only the radius number is checked (not the full wording), since the
 * production class sends a {@link TranslatableComponent} keyed
 * {@code skygridng.challenge.level_is_border.actionbar} with the radius as its single translation
 * argument (a {@link TextComponent}).
 */
class LevelBorderWarningTaskTest {

    private ServerMock server;
    private WorldMock world;
    private ChallengeAdminUseCase challenges;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("skygrid-world");
        challenges = mock(ChallengeAdminUseCase.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private LevelBorderWarningTask task(Predicate<World> appliesTo) {
        return new LevelBorderWarningTask(challenges, appliesTo, server);
    }

    private int radiusIn(Component message) {
        assertThat(message).isInstanceOf(TranslatableComponent.class);
        TranslatableComponent translatable = (TranslatableComponent) message;
        assertThat(translatable.key()).isEqualTo("skygridng.challenge.level_is_border.actionbar");
        assertThat(translatable.arguments()).hasSize(1);
        Component radiusComponent = (Component) translatable.arguments().getFirst().value();
        assertThat(radiusComponent).isInstanceOf(TextComponent.class);
        return Integer.parseInt(((TextComponent) radiusComponent).content().replaceAll("\\D+", ""));
    }

    @Test
    @DisplayName("radiusFor is highestLevel times BLOCKS_PER_LEVEL")
    void radiusFormula() {
        assertThat(LevelBorderWarningTask.radiusFor(0)).isEqualTo(0);
        assertThat(LevelBorderWarningTask.radiusFor(1)).isEqualTo(LevelBorderWarningTask.BLOCKS_PER_LEVEL);
        assertThat(LevelBorderWarningTask.radiusFor(5)).isEqualTo(5 * LevelBorderWarningTask.BLOCKS_PER_LEVEL);
    }

    @Test
    @DisplayName("an inactive challenge sends no actionbar")
    void inactiveSendsNoActionBar() {
        when(challenges.isActive(LevelBorderWarningTask.CHALLENGE_ID)).thenReturn(false);
        PlayerMock player = server.addPlayer();
        player.setLevel(3);

        task(w -> true).run();

        assertThat(player.nextActionBar()).isNull();
    }

    @Test
    @DisplayName("no online players in a matching world sends no actionbar")
    void noMatchingPlayersSendsNoActionBar() {
        when(challenges.isActive(LevelBorderWarningTask.CHALLENGE_ID)).thenReturn(true);
        PlayerMock player = server.addPlayer();
        player.setLevel(5);

        task(w -> false).run();

        assertThat(player.nextActionBar()).isNull();
    }

    @Test
    @DisplayName("an active challenge sends the actionbar with the level-based radius to matching players")
    void activeSendsActionBarWithRadius() {
        when(challenges.isActive(LevelBorderWarningTask.CHALLENGE_ID)).thenReturn(true);
        PlayerMock player = server.addPlayer();
        player.setLevel(4);

        task(w -> true).run();

        Component message = player.nextActionBar();
        assertThat(message).isNotNull();
        assertThat(radiusIn(message)).isEqualTo(LevelBorderWarningTask.radiusFor(4));
    }

    @Test
    @DisplayName("the highest level among several matching players drives the radius")
    void activeUsesHighestLevelAmongMatchingPlayers() {
        when(challenges.isActive(LevelBorderWarningTask.CHALLENGE_ID)).thenReturn(true);
        PlayerMock low = server.addPlayer();
        low.setLevel(2);
        PlayerMock high = server.addPlayer();
        high.setLevel(7);

        task(w -> true).run();

        int expectedRadius = LevelBorderWarningTask.radiusFor(7);
        assertThat(radiusIn(low.nextActionBar())).isEqualTo(expectedRadius);
        assertThat(radiusIn(high.nextActionBar())).isEqualTo(expectedRadius);
    }
}

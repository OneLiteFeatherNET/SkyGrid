package net.onelitefeather.skygridng.platforms.olf.scoreboard;

import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.Task;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.TaskProgressUseCase;
import net.onelitefeather.skygridng.ports.in.TaskProgressUseCase.TaskCompletionListener;
import net.onelitefeather.skygridng.ports.in.TaskProgressUseCase.TaskStatus;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MockBukkit + Mockito tests for the driving adapter. MockBukkit supplies the plugin only. The
 * {@link Player} and the whole {@code Player.getServer().getScoreboardManager()} chain (a
 * {@link Scoreboard}, its {@link Objective}, and each line's {@link Score}) are plain Mockito mocks,
 * the same "hand-mock the Bukkit interface MockBukkit does not model precisely enough" approach
 * {@code adapters.spawn.SpreadSpawnListenerTest} uses for its {@code World}: at the time of writing,
 * the resolved MockBukkit release's {@code ScoreMock} throws {@code UnimplementedOperationException}
 * (a {@code TestAbortedException} subtype, so JUnit reports it as a silently skipped test rather than
 * a failure) from both {@code customName(Component)} and {@code numberFormat(NumberFormat)}, exactly
 * the two calls this listener depends on. Mocking the chain directly keeps every assertion below
 * genuinely exercised.
 */
class ChallengeSidebarListenerTest {

    private static final String CHALLENGE_ID = "trial_of_the_grid";

    private JavaPlugin plugin;
    private TaskProgressUseCase taskProgress;
    private ChallengeAdminUseCase challenges;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
        taskProgress = mock(TaskProgressUseCase.class);
        challenges = mock(ChallengeAdminUseCase.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ChallengeSidebarListener listener() {
        return new ChallengeSidebarListener(taskProgress, challenges, plugin);
    }

    private ChallengeRule activeChallenge() {
        return new ChallengeRule(CHALLENGE_ID, "Trial of the Grid", true, List.of(), false);
    }

    private Task task(String id, String description) {
        return new Task(id, description, null);
    }

    /** Captures the {@link TaskCompletionListener} the adapter registers with the port on enable(). */
    private TaskCompletionListener captureCompletionListener(ChallengeSidebarListener listener) {
        listener.enable();
        ArgumentCaptor<TaskCompletionListener> captor = ArgumentCaptor.forClass(TaskCompletionListener.class);
        verify(taskProgress).addCompletionListener(captor.capture());
        return captor.getValue();
    }

    /**
     * A fully Mockito-mocked player whose {@code getServer().getScoreboardManager().getNewScoreboard()}
     * returns a fresh mocked {@link Scoreboard} each call, and whose one {@link Objective} hands out a
     * distinct {@link Score} mock per entry string, remembered in {@code scoresByEntry} so a test can
     * inspect or verify against the exact line a task id maps to.
     */
    private record MockedPlayer(Player player, Objective objective, Map<String, Score> scoresByEntry) {
    }

    private MockedPlayer mockedPlayer() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Server serverMock = mock(Server.class);
        when(player.getServer()).thenReturn(serverMock);
        ScoreboardManager manager = mock(ScoreboardManager.class);
        when(serverMock.getScoreboardManager()).thenReturn(manager);
        Scoreboard scoreboard = mock(Scoreboard.class);
        when(manager.getNewScoreboard()).thenReturn(scoreboard);
        Objective objective = mock(Objective.class);
        when(scoreboard.registerNewObjective(anyString(), any(Criteria.class), any(Component.class)))
                .thenReturn(objective);
        Map<String, Score> scoresByEntry = new HashMap<>();
        when(objective.getScore(anyString())).thenAnswer(invocation -> {
            String entry = invocation.getArgument(0);
            return scoresByEntry.computeIfAbsent(entry, id -> mock(Score.class));
        });
        return new MockedPlayer(player, objective, scoresByEntry);
    }

    @Test
    @DisplayName("on join with an active challenge, the sidebar shows every task in configured order")
    void joinWithActiveChallengeBuildsSidebar() {
        when(challenges.list()).thenReturn(List.of(activeChallenge()));
        Task first = task("mine_stone", "Mine a stone block");
        Task second = task("place_torch", "Place a torch");
        MockedPlayer mocked = mockedPlayer();
        when(taskProgress.tasksFor(mocked.player().getUniqueId(), CHALLENGE_ID)).thenReturn(
                List.of(new TaskStatus(first, false), new TaskStatus(second, true)));

        listener().onJoin(new PlayerJoinEvent(mocked.player(), Component.empty()));

        verify(mocked.objective()).setDisplaySlot(DisplaySlot.SIDEBAR);
        verify(mocked.player()).setScoreboard(any(Scoreboard.class));
        assertThat(mocked.scoresByEntry()).containsOnlyKeys(first.id(), second.id());

        ArgumentCaptor<Integer> firstScore = ArgumentCaptor.forClass(Integer.class);
        verify(mocked.scoresByEntry().get(first.id())).setScore(firstScore.capture());
        ArgumentCaptor<Integer> secondScore = ArgumentCaptor.forClass(Integer.class);
        verify(mocked.scoresByEntry().get(second.id())).setScore(secondScore.capture());
        // First task in tasksFor's configured order renders on top: highest score in a vanilla sidebar.
        assertThat(firstScore.getValue()).isGreaterThan(secondScore.getValue());
        verify(mocked.scoresByEntry().get(first.id())).customName(any(Component.class));
        verify(mocked.scoresByEntry().get(second.id())).customName(any(Component.class));
    }

    @Test
    @DisplayName("on join with no active challenge, no sidebar objective is set")
    void joinWithNoActiveChallengeShowsNoSidebar() {
        when(challenges.list()).thenReturn(List.of(activeChallenge().withActive(false)));
        MockedPlayer mocked = mockedPlayer();

        listener().onJoin(new PlayerJoinEvent(mocked.player(), Component.empty()));

        verify(mocked.player(), never()).setScoreboard(any());
        verify(taskProgress, never()).tasksFor(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("a completion callback updates only the affected line, without calling tasksFor again")
    void completionUpdatesOnlyAffectedLine() {
        when(challenges.list()).thenReturn(List.of(activeChallenge()));
        Task first = task("mine_stone", "Mine a stone block");
        Task second = task("place_torch", "Place a torch");
        MockedPlayer mocked = mockedPlayer();
        UUID playerId = mocked.player().getUniqueId();
        when(taskProgress.tasksFor(playerId, CHALLENGE_ID)).thenReturn(
                List.of(new TaskStatus(first, false), new TaskStatus(second, false)));
        ChallengeSidebarListener listener = listener();
        TaskCompletionListener completionListener = captureCompletionListener(listener);
        listener.onJoin(new PlayerJoinEvent(mocked.player(), Component.empty()));

        completionListener.onTaskCompleted(playerId, CHALLENGE_ID, first.id());

        // customName is called once at join (both lines) and exactly once more, for the completed line.
        verify(mocked.scoresByEntry().get(first.id()), times(2)).customName(any(Component.class));
        verify(mocked.scoresByEntry().get(second.id()), times(1)).customName(any(Component.class));
        // tasksFor was called exactly once, at join; the completion patch never re-queries it.
        verify(taskProgress, times(1)).tasksFor(playerId, CHALLENGE_ID);

        listener.disable();
    }

    @Test
    @DisplayName("a completion for a challenge that is no longer this player's shown board is ignored")
    void staleCompletionForADifferentChallengeIsIgnored() {
        when(challenges.list()).thenReturn(List.of(activeChallenge()));
        Task first = task("mine_stone", "Mine a stone block");
        MockedPlayer mocked = mockedPlayer();
        UUID playerId = mocked.player().getUniqueId();
        when(taskProgress.tasksFor(playerId, CHALLENGE_ID)).thenReturn(List.of(new TaskStatus(first, false)));
        ChallengeSidebarListener listener = listener();
        TaskCompletionListener completionListener = captureCompletionListener(listener);
        listener.onJoin(new PlayerJoinEvent(mocked.player(), Component.empty()));

        // Simulates the active challenge having changed since this player joined.
        completionListener.onTaskCompleted(playerId, "a_different_challenge", first.id());

        // customName was only ever called once, at join; the stale completion never touched the line.
        verify(mocked.scoresByEntry().get(first.id()), times(1)).customName(any(Component.class));

        listener.disable();
    }

    @Test
    @DisplayName("quit cleans up bookkeeping and a later completion for that player does not throw")
    void quitCleansUpBookkeeping() {
        when(challenges.list()).thenReturn(List.of(activeChallenge()));
        Task first = task("mine_stone", "Mine a stone block");
        MockedPlayer mocked = mockedPlayer();
        UUID playerId = mocked.player().getUniqueId();
        when(taskProgress.tasksFor(playerId, CHALLENGE_ID)).thenReturn(List.of(new TaskStatus(first, false)));
        ChallengeSidebarListener listener = listener();
        TaskCompletionListener completionListener = captureCompletionListener(listener);
        listener.onJoin(new PlayerJoinEvent(mocked.player(), Component.empty()));

        listener.onQuit(new PlayerQuitEvent(mocked.player(), Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThatCode(() -> completionListener.onTaskCompleted(playerId, CHALLENGE_ID, first.id()))
                .doesNotThrowAnyException();
        // The board was removed on quit, so the stale-looking completion never reached the mock line.
        verify(mocked.scoresByEntry().get(first.id()), times(1)).customName(any(Component.class));

        listener.disable();
    }
}

package net.onelitefeather.skygridng.adapters.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import jakarta.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.Task;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.TaskProgressUseCase;
import net.onelitefeather.skygridng.ports.in.TaskProgressUseCase.TaskStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Driving adapter: shows a player their active challenge's task progress as a live, per-player
 * scoreboard sidebar.
 *
 * Purely event-driven, never a repeating task: the sidebar is built once on join from
 * {@link TaskProgressUseCase#tasksFor} (the only place this class calls it) and afterward only the
 * one changed line is patched in place, via {@link TaskProgressUseCase#addCompletionListener}. This
 * matters for scale: on a server with many players and long-running challenges, a repeating
 * re-render would be wasted work on every tick for boards that rarely change, whereas a targeted
 * one-line patch costs nothing until a task actually completes.
 *
 * Each online player gets their own fresh {@link Scoreboard} (never the server's shared main
 * scoreboard), so one player's quest board never leaks into another player's view.
 *
 * <p>Bookkeeping: one {@link PlayerBoard} per currently online player with a shown sidebar, keyed by
 * player id. It carries the challenge id the board was built for (a staleness guard: if the active
 * challenge changes after a player has joined, a completion event for the old challenge is ignored
 * instead of touching a board it no longer describes) plus, per task id, both the {@link Score} line
 * to patch and the {@link Task} itself (so {@link #onTaskCompleted} can rebuild that one line's text
 * without calling {@link TaskProgressUseCase#tasksFor} again). {@link #onQuit} removes the entry so
 * the map cannot grow unbounded across many joins and quits on a long-running server.
 *
 * <p>Scoreboard lines are keyed by the task id itself (a per-challenge unique, stable string), used
 * both as the {@link Objective#getScore(String) entry} and as the {@code PlayerBoard} lookup key.
 * Display order follows vanilla sidebar sorting: entries render highest score first (top of the
 * board), ties broken alphabetically by entry, so the first task in {@code tasksFor}'s configured
 * order is given the highest score and counts down from there. {@link Score#numberFormat} is set to
 * {@link NumberFormat#blank()} so the score value itself, used only for ordering, never appears next
 * to the line.
 *
 * <p>Assumes the completion listener fires on the main server thread, consistent with every other
 * synchronous, event-driven port callback in this codebase (for example
 * {@link ChallengeAdminUseCase#addChangeListener}): {@link Score#customName} is a Bukkit call and is
 * not thread-safe. If a future {@code TaskProgressUseCase} implementation ever completes tasks from a
 * background thread, the update in {@link #onTaskCompleted} would need to be scheduled back onto the
 * main thread first.
 *
 * <p>The 15-line client render cap on a sidebar objective is not addressed here (no truncation): the
 * challenges shipped in this slice have only 1-2 tasks each, so it never comes into play.
 */
public final class ChallengeSidebarListener implements Listener {

    private static final String OBJECTIVE_NAME = "skygridng_quest";

    private final TaskProgressUseCase taskProgress;
    private final ChallengeAdminUseCase challenges;
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerBoard> boards = new ConcurrentHashMap<>();

    @Inject
    public ChallengeSidebarListener(TaskProgressUseCase taskProgress, ChallengeAdminUseCase challenges, JavaPlugin plugin) {
        this.taskProgress = taskProgress;
        this.challenges = challenges;
        this.plugin = plugin;
    }

    /** Called once after the base generator is wired. Registers this listener and the completion hook. */
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        taskProgress.addCompletionListener(this::onTaskCompleted);
    }

    /** Called once on shutdown. Releases everything {@link #enable()} acquired. */
    public void disable() {
        HandlerList.unregisterAll(this);
        boards.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Optional<ChallengeRule> active = challenges.list().stream().filter(ChallengeRule::active).findFirst();
        if (active.isEmpty()) {
            return; // no active challenge: no sidebar is shown, the player keeps whatever board they had
        }
        ChallengeRule challenge = active.get();
        Player player = event.getPlayer();
        List<TaskStatus> statuses = taskProgress.tasksFor(player.getUniqueId(), challenge.id());

        Scoreboard scoreboard = player.getServer().getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title(challenge));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Map<String, Score> scores = new ConcurrentHashMap<>();
        Map<String, Task> tasks = new ConcurrentHashMap<>();
        int lineCount = statuses.size();
        for (int index = 0; index < lineCount; index++) {
            TaskStatus status = statuses.get(index);
            Task task = status.task();
            Score score = objective.getScore(task.id());
            score.customName(lineText(task, status.done()));
            score.numberFormat(NumberFormat.blank());
            // Highest score renders at the top of a vanilla sidebar, so the first task in tasksFor's
            // configured order gets the highest value and later tasks count down from there.
            score.setScore(lineCount - index);
            scores.put(task.id(), score);
            tasks.put(task.id(), task);
        }

        player.setScoreboard(scoreboard);
        boards.put(player.getUniqueId(), new PlayerBoard(challenge.id(), scores, tasks));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Registered as the {@link TaskProgressUseCase.TaskCompletionListener}: patches the one changed
     * line in place. Never calls {@link TaskProgressUseCase#tasksFor} again and never rebuilds the
     * board, by design (see the class Javadoc).
     */
    private void onTaskCompleted(UUID playerId, String challengeId, String taskId) {
        PlayerBoard board = boards.get(playerId);
        if (board == null || !board.challengeId().equals(challengeId)) {
            return; // offline, or a stale update for a challenge this player's board no longer shows
        }
        Score score = board.scores().get(taskId);
        Task task = board.tasks().get(taskId);
        if (score == null || task == null) {
            return; // taskId not part of this player's rendered board
        }
        score.customName(lineText(task, true));
    }

    private Component title(ChallengeRule challenge) {
        String text = challenge.description().isBlank() ? challenge.id() : challenge.description();
        return Component.text(text, NamedTextColor.GOLD);
    }

    private Component lineText(Task task, boolean done) {
        String text = task.description().isBlank() ? task.id() : task.description();
        Component prefix = done
                ? Component.text("✓ ", NamedTextColor.GREEN)
                : Component.text("✗ ", NamedTextColor.RED);
        return prefix.append(Component.text(text, done ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE));
    }

    /**
     * One player's currently shown sidebar: the challenge it was built for (the staleness guard) and,
     * per task id, both the {@link Score} line to patch and the {@link Task} needed to rebuild that
     * line's text without a second {@link TaskProgressUseCase#tasksFor} call.
     */
    private record PlayerBoard(String challengeId, Map<String, Score> scores, Map<String, Task> tasks) {
    }
}

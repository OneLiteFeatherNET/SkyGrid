package net.onelitefeather.skygridng.platforms.common;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import net.onelitefeather.skygridng.adapters.persistence.AbstractJpaRepository;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.api.model.ProgressScope;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonRun;
import net.onelitefeather.skygridng.platforms.common.injection.RepositoryModule;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import net.onelitefeather.skygridng.ports.out.SeasonRunRepository;
import net.onelitefeather.skygridng.ports.out.TaskCompletionRepository;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plugin warms both Postgres backed repositories up on an async thread right after it enables,
 * so the first command or task completion never runs the Flyway migration on the main thread. The
 * databases here are probes that are always down: they record each bootstrap attempt and fail the
 * way a missing {@code SKYGRID_DB_*} variable or an unreachable Postgres does.
 */
class DatabaseWarmUpTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enablingWarmsBothDatabasesUpOffTheEnablingThread() throws InvalidDescriptionException {
        var enablingThread = Thread.currentThread();

        var plugin = loadProbedPlugin();
        server.getScheduler().waitAsyncTasksFinished();

        assertThat(plugin.challengeDatabase.bootstrapThreads)
                .as("the challenge-progress database must be warmed up exactly once at enable")
                .hasSize(1);
        assertThat(plugin.taskDatabase.bootstrapThreads)
                .as("the task-progress database must be warmed up exactly once at enable")
                .hasSize(1);
        assertThat(plugin.seasonDatabase.bootstrapThreads)
                .as("the season run database must be warmed up exactly once at enable")
                .hasSize(1);
        assertThat(plugin.challengeDatabase.bootstrapThreads)
                .as("the warm-up must not run on the thread that enables the plugin")
                .doesNotContain(enablingThread);
    }

    @Test
    void aFailedWarmUpIsLoggedOncePerDatabaseAndLeavesThePluginEnabled() throws InvalidDescriptionException {
        var plugin = loadProbedPlugin();
        server.getScheduler().waitAsyncTasksFinished();

        assertThat(plugin.isEnabled())
                .as("a failed warm-up must not disable the plugin")
                .isTrue();
        assertThat(plugin.log.warnings())
                .filteredOn(record -> record.getMessage().startsWith("Database warm-up failed"))
                .as("each database that fails to warm up must produce one warning naming it")
                .extracting(LogRecord::getMessage)
                .anySatisfy(message -> assertThat(message).contains("challenge-progress database is down"))
                .anySatisfy(message -> assertThat(message).contains("task-progress database is down"))
                .anySatisfy(message -> assertThat(message).contains("season-run database is down"))
                .hasSize(3);
    }

    /**
     * MockBukkit cannot find a plugin.yml whose main class is the test subclass, so the description is
     * given explicitly, naming the subclass as the main class.
     */
    private static ProbedPlugin loadProbedPlugin() throws InvalidDescriptionException {
        var description = new PluginDescriptionFile(new StringReader(
                "name: SkyGrid-ng\nversion: test\napi-version: '1.21'\nmain: " + ProbedPlugin.class.getName() + "\n"));
        return MockBukkit.loadWith(ProbedPlugin.class, description);
    }

    /** Stands in for a Postgres backed database that is down: it records each bootstrap attempt and fails. */
    static final class DownDatabase implements Supplier<DatabaseConfig> {

        private final String unit;
        private final List<Thread> bootstrapThreads = new CopyOnWriteArrayList<>();

        DownDatabase(String unit) {
            this.unit = unit;
        }

        @Override
        public DatabaseConfig get() {
            bootstrapThreads.add(Thread.currentThread());
            throw new IllegalStateException(unit + " database is down");
        }
    }

    static final class ProbeChallengeRepository extends AbstractJpaRepository implements ChallengeProgressRepository {

        ProbeChallengeRepository(DownDatabase database) {
            super("challenge-progress", "classpath:db/migration/challenge-progress", "flyway_history_probe", database);
        }

        @Override
        public int tierOf(SeasonId season, UUID playerId, String challengeId) {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public int recordCompletion(SeasonId season, UUID playerId, String challengeId) {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public void resetSeason(SeasonId season) {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public Map<SeasonId, Integer> history(UUID playerId) {
            throw new UnsupportedOperationException("probe");
        }
    }

    static final class ProbeTaskRepository extends AbstractJpaRepository implements TaskCompletionRepository {

        ProbeTaskRepository(DownDatabase database) {
            super("task-progress", "classpath:db/migration/task-progress", "flyway_history_probe_task", database);
        }

        @Override
        public boolean isCompleted(ProgressScope scope, String taskId) {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public boolean markCompleted(ProgressScope scope, String taskId) {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public void resetSeason(SeasonId season) {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public Map<SeasonId, Long> completedCountBySeason(UUID playerId) {
            throw new UnsupportedOperationException("probe");
        }
    }

    /** The plugin with its two Postgres repositories replaced by the always-down probes. */
    public static class ProbedPlugin extends SkyGridNgPlugin {

        final DownDatabase challengeDatabase = new DownDatabase("challenge-progress");
        final DownDatabase taskDatabase = new DownDatabase("task-progress");
        final DownDatabase seasonDatabase = new DownDatabase("season-run");
        final CapturingHandler log = new CapturingHandler();

        public ProbedPlugin() {
            // Attached before any load or enable call, so no warm-up warning can be missed.
            getLogger().addHandler(log);
        }

        @Override
        protected List<Module> modules() {
            List<Module> modules = new ArrayList<>(super.modules());
            modules.replaceAll(module -> module instanceof RepositoryModule
                    ? Modules.override(module).with(new ProbeModule())
                    : module);
            return modules;
        }

        private final class ProbeModule extends AbstractModule {

            @Override
            protected void configure() {
                bind(ChallengeProgressRepository.class).toInstance(new ProbeChallengeRepository(challengeDatabase));
                bind(TaskCompletionRepository.class).toInstance(new ProbeTaskRepository(taskDatabase));
                bind(SeasonRunRepository.class).toInstance(new ProbeSeasonRepository(seasonDatabase));
            }
        }
    }

    static final class ProbeSeasonRepository extends AbstractJpaRepository implements SeasonRunRepository {

        ProbeSeasonRepository(DownDatabase database) {
            super("season", "classpath:db/migration/season", "flyway_history_probe_season", database);
        }

        @Override
        public Optional<SeasonRun> find(SeasonId seasonId) {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public Optional<SeasonRun> active() {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public List<SeasonRun> inFlight() {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public void save(SeasonRun run) {
            throw new UnsupportedOperationException("probe");
        }

        @Override
        public void switchActive(SeasonId from, SeasonId to, Instant at) {
            throw new UnsupportedOperationException("probe");
        }
    }

    /** Collects the warnings the plugin logs, so the test asserts on the records, not on console output. */
    static final class CapturingHandler extends Handler {

        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> warnings() {
            return records.stream().filter(record -> record.getLevel() == Level.WARNING).toList();
        }
    }
}

package net.onelitefeather.skygridng.platforms.common;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import net.onelitefeather.skygridng.adapters.persistence.Database;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.platforms.common.injection.RepositoryModule;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plugin warms the one shared database up on an async thread right after it enables, so the first
 * command or task completion never runs the Flyway migration on the main thread. The database here is a
 * probe that is always down: it records each bootstrap attempt and fails the way a missing
 * {@code SKYGRID_DB_*} variable or an unreachable Postgres does.
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
    void enablingWarmsTheDatabaseUpOnceOffTheEnablingThread() throws InvalidDescriptionException {
        var enablingThread = Thread.currentThread();

        var plugin = loadProbedPlugin();
        server.getScheduler().waitAsyncTasksFinished();

        assertThat(plugin.database.bootstrapThreads)
                .as("the shared database must be warmed up exactly once at enable")
                .hasSize(1);
        assertThat(plugin.database.bootstrapThreads)
                .as("the warm-up must not run on the thread that enables the plugin")
                .doesNotContain(enablingThread);
    }

    @Test
    void aFailedWarmUpIsLoggedOnceAndLeavesThePluginEnabled() throws InvalidDescriptionException {
        var plugin = loadProbedPlugin();
        server.getScheduler().waitAsyncTasksFinished();

        assertThat(plugin.isEnabled())
                .as("a failed warm-up must not disable the plugin")
                .isTrue();
        assertThat(plugin.log.warnings())
                .filteredOn(record -> record.getMessage().startsWith("Database warm-up failed"))
                .as("the database that fails to warm up must produce one warning")
                .hasSize(1)
                .extracting(LogRecord::getMessage)
                .allSatisfy(message -> assertThat(message).contains("database is down"));
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

    /** Stands in for the Postgres database while it is down: it records each bootstrap attempt and fails. */
    static final class DownDatabase implements Supplier<DatabaseConfig> {

        private final List<Thread> bootstrapThreads = new CopyOnWriteArrayList<>();

        @Override
        public DatabaseConfig get() {
            bootstrapThreads.add(Thread.currentThread());
            throw new IllegalStateException("skygrid database is down");
        }
    }

    /** The plugin with its one database replaced by the always-down probe. */
    public static class ProbedPlugin extends SkyGridNgPlugin {

        final DownDatabase database = new DownDatabase();
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
                bind(Database.class).toInstance(new Database(database));
            }
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

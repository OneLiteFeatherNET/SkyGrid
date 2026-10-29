package net.onelitefeather.skygridng.season;

import net.onelitefeather.skygridng.platforms.common.DatabaseWarmUpGate;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.scheduler.RepeatingTask;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Black-box tests for the scheduler entry point. MockBukkit drives the ticks explicitly and the async part is
 * awaited, never slept for. The clock is fixed.
 */
class SeasonTickerTest {

    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");

    private ServerMock server;
    private Plugin plugin;
    private SeasonAdminUseCase admin;
    private DatabaseWarmUpGate gate;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
        admin = mock(SeasonAdminUseCase.class);
        when(admin.tick(any())).thenReturn(false);
        gate = new DatabaseWarmUpGate();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SeasonTicker ticker() {
        return new SeasonTicker(plugin, admin, Clock.fixed(NOW, ZoneOffset.UTC), gate);
    }

    @Test
    void startSchedulesOneRepeatingRunEvery600Ticks() {
        ticker().start();

        assertThat(server.getScheduler().getPendingTasks())
                .as("the ticker schedules exactly one repeating task with the 600 tick period")
                .singleElement()
                .isInstanceOfSatisfying(RepeatingTask.class,
                        task -> assertThat(task.getPeriod()).isEqualTo(SeasonTicker.PERIOD_TICKS));
    }

    @Test
    void aRunHandsTheClockInstantToTheService() {
        gate.markWarmedUp();

        ticker().run();
        server.getScheduler().waitAsyncTasksFinished();

        verify(admin, times(1)).tick(NOW);
    }

    @Test
    void noTickRunsBeforeTheDatabaseWarmUpFinished() {
        ticker().start();

        server.getScheduler().performTicks(1);
        server.getScheduler().waitAsyncTasksFinished();

        verify(admin, never()).tick(any());
    }
}

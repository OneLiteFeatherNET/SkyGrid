package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.SpawnConfig;
import net.onelitefeather.skygridng.api.model.SpawnPoint;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spiral spawn planner is the deterministic, platform-neutral heart of spread-spawn: distinct
 * players never share a slot and are always at least one spacing apart, so they cannot see each
 * other on a shared world.
 */
class SpiralSpawnServiceTest {

    private static final int SPACING = 100;

    private SpiralSpawnService service() {
        SpawnConfig spawn = new SpawnConfig(true, SPACING, 70, BlockId.of("minecraft:stone"), 1, true);
        RuleState state = new RuleState(Fixtures.config("skygrid").withSpawn(spawn), 4);
        return new SpiralSpawnService(state);
    }

    @Test
    @DisplayName("the first player spawns at the origin")
    void firstPlayerAtOrigin() {
        assertThat(service().at(0)).isEqualTo(new SpawnPoint(0, 0));
    }

    @Test
    @DisplayName("slots scale with the configured spacing and are deterministic")
    void scalesWithSpacingAndDeterministic() {
        SpiralSpawnService service = service();
        for (int ordinal = 0; ordinal < 50; ordinal++) {
            SpawnPoint point = service.at(ordinal);
            assertThat(point.x() % SPACING).isZero();
            assertThat(point.z() % SPACING).isZero();
            assertThat(service.at(ordinal)).isEqualTo(point);
        }
    }

    @Test
    @DisplayName("distinct players get distinct slots, at least one spacing apart")
    void slotsAreDistinctAndSpacedOut() {
        SpiralSpawnService service = service();
        Set<SpawnPoint> seen = new HashSet<>();
        for (int a = 0; a < 60; a++) {
            SpawnPoint pa = service.at(a);
            assertThat(seen.add(pa)).as("ordinal %d is a fresh slot", a).isTrue();
            for (int b = 0; b < a; b++) {
                SpawnPoint pb = service.at(b);
                double distance = Math.hypot(pa.x() - (double) pb.x(), pa.z() - (double) pb.z());
                assertThat(distance).as("%d vs %d", a, b).isGreaterThanOrEqualTo(SPACING);
            }
        }
    }

    @Test
    @DisplayName("the planner reflects the live spawn config")
    void exposesConfig() {
        SpiralSpawnService service = service();
        assertThat(service.spread()).isTrue();
        assertThat(service.platformY()).isEqualTo(70);
        assertThat(service.platformBlock()).isEqualTo(BlockId.of("minecraft:stone"));
        assertThat(service.platformRadius()).isEqualTo(1);
        assertThat(service.spawnVillager()).isTrue();
    }
}

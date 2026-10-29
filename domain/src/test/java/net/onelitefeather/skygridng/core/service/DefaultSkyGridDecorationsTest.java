package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.ports.in.SkyGridDecorations;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the classic-grid block-entity decoration decisions: it exposes the grid bounds,
 * and picks a configured loot table and mob deterministically per coordinate.
 */
class DefaultSkyGridDecorationsTest {

    private SkyGridDecorations decorations() {
        return new DefaultSkyGridDecorations(new RuleState(Fixtures.config("skygrid"), 4));
    }

    @Test
    @DisplayName("exposes the configured grid bounds and is active when loot or mobs are configured")
    void gridAndActive() {
        SkyGridDecorations d = decorations();
        assertThat(d.active()).isTrue();
        assertThat(d.spacing()).isEqualTo(4);
        assertThat(d.minY()).isEqualTo(0);
        assertThat(d.maxY()).isEqualTo(64);
    }

    @Test
    @DisplayName("picks a configured loot table and mob, deterministically per coordinate")
    void deterministicPicks() {
        SkyGridDecorations d = decorations();
        long seed = Fixtures.SEED;

        BlockId loot = d.lootTableAt(seed, 8, 16, 12).orElseThrow();
        BlockId mob = d.spawnerMobAt(seed, 8, 16, 12).orElseThrow();

        assertThat(Set.of("minecraft:chests/simple_dungeon", "minecraft:chests/jungle_temple"))
                .contains(loot.asString());
        assertThat(Set.of("minecraft:zombie", "minecraft:blaze")).contains(mob.asString());

        // same coordinate -> same pick
        assertThat(d.lootTableAt(seed, 8, 16, 12)).contains(loot);
        assertThat(d.spawnerMobAt(seed, 8, 16, 12)).contains(mob);
    }

    @Test
    @DisplayName("scatters a self-contained sugar cane oasis on a fraction of cells, deterministically")
    void scattersOasis() {
        SkyGridDecorations d = decorations();
        long seed = Fixtures.SEED;
        int hits = 0;
        for (int x = 0; x < 256; x += 4) {
            for (int z = 0; z < 256; z += 4) {
                var plant = d.plantAt(seed, x, 16, z);
                if (plant.isPresent()) {
                    hits++;
                    assertThat(plant.get().material().asString()).isEqualTo("minecraft:sugar_cane");
                    assertThat(plant.get().providesWater()).isTrue();
                    assertThat(d.plantAt(seed, x, 16, z)).isEqualTo(plant); // deterministic
                }
            }
        }
        assertThat(hits).isGreaterThan(0); // some cells get an oasis (~4% chance)
    }
}


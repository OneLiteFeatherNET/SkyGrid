package net.onelitefeather.skygridng.core.model;

import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * White-box tests for the bitmask adjacency model. Tile order in the fixture is
 * water(0), sand(1), grass(2), cactus(3); pairs are water-sand, sand-grass, sand-cactus,
 * grass-cactus.
 */
class AdjacencyRulesTest {

    private static final Direction ANY = Direction.values()[0];

    @Test
    @DisplayName("support lists the allowed neighbours of a single tile, with self when allowSelf")
    void supportWithSelf() {
        AdjacencyRules adjacency = AdjacencyRules.from(Fixtures.tiles(), Fixtures.pairs(), true);
        assertThat(adjacency.tileCount()).isEqualTo(4);
        // water can border water (self) and sand -> bits 0,1
        assertThat(adjacency.support(1L << 0, ANY)).isEqualTo(0b0011L);
        // sand borders everything in this fixture (+ self) -> bits 0,1,2,3
        assertThat(adjacency.support(1L << 1, ANY)).isEqualTo(0b1111L);
        // cactus borders sand and grass (+ self) -> bits 1,2,3
        assertThat(adjacency.support(1L << 3, ANY)).isEqualTo(0b1110L);
    }

    @Test
    @DisplayName("without allowSelf the self bit is absent")
    void supportWithoutSelf() {
        AdjacencyRules adjacency = AdjacencyRules.from(Fixtures.tiles(), Fixtures.pairs(), false);
        assertThat(adjacency.support(1L << 0, ANY)).isEqualTo(0b0010L); // water -> sand only
    }

    @Test
    @DisplayName("support over a multi tile domain is the union of each tile's neighbours")
    void supportIsUnion() {
        AdjacencyRules adjacency = AdjacencyRules.from(Fixtures.tiles(), Fixtures.pairs(), false);
        long union = adjacency.support((1L << 0) | (1L << 2), ANY); // {water, grass}
        long expected = adjacency.support(1L << 0, ANY) | adjacency.support(1L << 2, ANY);
        assertThat(union).isEqualTo(expected);
    }

    @Test
    @DisplayName("more than 64 tiles is rejected")
    void rejectsTooManyTiles() {
        List<TileType> tooMany = new ArrayList<>();
        for (int i = 0; i < AdjacencyRules.MAX_TILES + 1; i++) {
            tooMany.add(new TileType("t" + i, 1.0, true, "STONE", "STONE", false, List.of()));
        }
        assertThatThrownBy(() -> AdjacencyRules.from(tooMany, Set.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package net.onelitefeather.skygridng.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for {@link StillWater}: it boxes a still water source into the first cardinal
 * neighbor of the plant's cell that can be fully contained (floor and 3 walls in region, the plant's
 * own cell being the fourth wall). Every expected placement below was confirmed against the real
 * compiled {@link StillWater#place} via a reflection driver before being asserted here (the neighbor
 * order, which coordinates each neighbor's floor/walls resolve to, and the "no neighbor works" case).
 */
class StillWaterTest {

    private static final BlockId SAND = BlockId.of("minecraft:sand");
    private static final BlockId WATER = BlockId.of("minecraft:water");

    @Test
    @DisplayName("boxes water into the first usable neighbor: floor, 3 walls, and the water source")
    void placesFirstUsableNeighbor() {
        FakeRegionWriter writer = new FakeRegionWriter();

        boolean placed = StillWater.place(writer, 5, 10, 5);

        assertThat(placed).isTrue();
        // First neighbor tried is {0,-1} (north, z-1): world (5, 10, 4).
        assertThat(writer.blockAt(5, 9, 4)).isEqualTo(SAND);   // floor
        assertThat(writer.blockAt(5, 10, 3)).isEqualTo(SAND);  // wall (further north)
        assertThat(writer.blockAt(4, 10, 4)).isEqualTo(SAND);  // wall (west)
        assertThat(writer.blockAt(6, 10, 4)).isEqualTo(SAND);  // wall (east)
        assertThat(writer.blockAt(5, 10, 4)).isEqualTo(WATER); // source
        // The plant's own cell (the fourth wall) is untouched by StillWater itself.
        assertThat(writer.placements()).noneMatch(p -> p.x() == 5 && p.y() == 10 && p.z() == 5);
    }

    @Test
    @DisplayName("skips a neighbor whose floor is out of region and tries the next one")
    void skipsUncontainedNeighbor() {
        FakeRegionWriter writer = new FakeRegionWriter();
        // North's floor (5, 9, 4) cannot be used, so it must fall through to south {0, 1}: (5, 10, 6).
        writer.excludeFromRegion(5, 9, 4);

        boolean placed = StillWater.place(writer, 5, 10, 5);

        assertThat(placed).isTrue();
        // North was skipped entirely: nothing placed there.
        assertThat(writer.placements()).noneMatch(p -> p.z() == 4);
        assertThat(writer.blockAt(5, 9, 6)).isEqualTo(SAND);   // floor
        assertThat(writer.blockAt(5, 10, 7)).isEqualTo(SAND);  // wall (further south)
        assertThat(writer.blockAt(4, 10, 6)).isEqualTo(SAND);  // wall (west)
        assertThat(writer.blockAt(6, 10, 6)).isEqualTo(SAND);  // wall (east)
        assertThat(writer.blockAt(5, 10, 6)).isEqualTo(WATER); // source
    }

    @Test
    @DisplayName("returns false and places nothing when no neighbor can be fully contained")
    void returnsFalseWhenNoNeighborWorks() {
        FakeRegionWriter writer = new FakeRegionWriter();
        // Exclude every neighbor's floor: north, south, west, east all fail canContain.
        writer.excludeFromRegion(5, 9, 4);
        writer.excludeFromRegion(5, 9, 6);
        writer.excludeFromRegion(4, 9, 5);
        writer.excludeFromRegion(6, 9, 5);

        boolean placed = StillWater.place(writer, 5, 10, 5);

        assertThat(placed).isFalse();
        assertThat(writer.placements()).isEmpty();
    }

    @Test
    @DisplayName("the plant's own cell is excluded from a neighbor's wall check, not required in region")
    void plantsOwnCellIsNotCheckedAsAWall() {
        FakeRegionWriter writer = new FakeRegionWriter();
        // The north neighbor's south wall would be the plant's own cell (5, 10, 5). Marking it out of
        // region must NOT block the north neighbor, since that check is skipped for the plant's cell.
        writer.excludeFromRegion(5, 10, 5);

        boolean placed = StillWater.place(writer, 5, 10, 5);

        assertThat(placed).isTrue();
        assertThat(writer.blockAt(5, 9, 4)).isEqualTo(SAND);
        assertThat(writer.blockAt(5, 10, 3)).isEqualTo(SAND);
        assertThat(writer.blockAt(4, 10, 4)).isEqualTo(SAND);
        assertThat(writer.blockAt(6, 10, 4)).isEqualTo(SAND);
        assertThat(writer.blockAt(5, 10, 4)).isEqualTo(WATER);
    }
}

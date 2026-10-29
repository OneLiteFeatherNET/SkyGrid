package net.onelitefeather.skygridng.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * White-box tests for {@link StructurePattern}'s own validation and geometry logic: the compact
 * constructor's guards (uniform box, legend coverage, reserved symbols, exactly one anchor, filler
 * required for {@code '#'}) and the grid helpers ({@link StructurePattern#cells()},
 * {@link StructurePattern#anchorOffset()}, {@link StructurePattern.Cell#offsetFrom(int[])}). Pure
 * domain logic in the {@code api} module: no Bukkit, no Jackson.
 */
class StructurePatternTest {

    private static final Map<Character, LegendEntry> SINGLE_ANCHOR = Map.of(
            'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true));

    // ----- valid construction -----

    @Test
    @DisplayName("a well formed pattern constructs without throwing")
    void validConstructionSucceeds() {
        StructurePattern pattern = new StructurePattern(
                List.of(List.of("###", "###"), List.of(" A ", " B.")),
                Map.of(
                        'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                        'B', new LegendEntry(BlockId.of("minecraft:water"), Map.of("level", "0"), false)),
                BlockId.of("minecraft:sandstone"));

        assertThat(pattern.height()).isEqualTo(2);
        assertThat(pattern.depth()).isEqualTo(2);
        assertThat(pattern.width()).isEqualTo(3);
    }

    @Test
    @DisplayName("a pattern with no ENSURE_SOLID cell does not require a filler")
    void fillerOptionalWithoutEnsureSolid() {
        StructurePattern pattern = new StructurePattern(List.of(List.of("A")), SINGLE_ANCHOR, null);

        assertThat(pattern.filler()).isNull();
    }

    // ----- uniform box -----

    @Test
    @DisplayName("a layer with a different row count than the others throws")
    void nonRectangularLayerThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(
                        List.of(List.of("AAA"), List.of("AA", "AA")), SINGLE_ANCHOR, null))
                .withMessageContaining("layer 1 has 2 rows, expected 1");
    }

    @Test
    @DisplayName("a row with a different length than the others throws")
    void nonRectangularRowThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(
                        List.of(List.of("AAA", "AA")), SINGLE_ANCHOR, null))
                .withMessageContaining("layer 0 row 1 has length 2, expected 3");
    }

    @Test
    @DisplayName("an empty layers list throws")
    void emptyLayersThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(List.of(), SINGLE_ANCHOR, null))
                .withMessageContaining("layers must not be empty");
    }

    // ----- legend coverage -----

    @Test
    @DisplayName("a grid symbol with no legend entry throws")
    void unknownSymbolThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(List.of(List.of("AXA")), SINGLE_ANCHOR, null))
                .withMessageContaining("unknown symbol 'X'");
    }

    @Test
    @DisplayName("each reserved symbol is rejected as a legend key")
    void reservedSymbolsRejectedAsLegendKeys() {
        for (char reserved : new char[] {StructurePattern.IGNORE, StructurePattern.ENSURE_SOLID,
                StructurePattern.CLEAR_AIR}) {
            Map<Character, LegendEntry> legend = Map.of(
                    reserved, new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true));
            assertThatIllegalArgumentException()
                    .as("reserved symbol '%s'", reserved)
                    .isThrownBy(() -> new StructurePattern(List.of(List.of("AAA")), legend, null))
                    .withMessageContaining("must not redefine reserved symbol '" + reserved + "'");
        }
    }

    // ----- anchor rules -----

    @Test
    @DisplayName("no legend entry marked anchor throws")
    void zeroAnchorEntriesThrows() {
        Map<Character, LegendEntry> legend = Map.of(
                'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(List.of(List.of("AAA")), legend, null))
                .withMessageContaining("must mark exactly one entry as anchor");
    }

    @Test
    @DisplayName("more than one legend entry marked anchor throws")
    void multipleAnchorEntriesThrows() {
        Map<Character, LegendEntry> legend = Map.of(
                'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                'B', new LegendEntry(BlockId.of("minecraft:dirt"), Map.of(), true));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(List.of(List.of("AB")), legend, null))
                .withMessageContaining("more than one anchor entry");
    }

    @Test
    @DisplayName("an anchor symbol that never appears in the grid throws")
    void anchorSymbolMissingFromGridThrows() {
        Map<Character, LegendEntry> legend = Map.of(
                'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                'B', new LegendEntry(BlockId.of("minecraft:dirt"), Map.of(), false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(List.of(List.of("BBB")), legend, null))
                .withMessageContaining("must appear exactly once in layers, found 0");
    }

    @Test
    @DisplayName("an anchor symbol that appears more than once in the grid throws")
    void anchorSymbolDuplicatedInGridThrows() {
        Map<Character, LegendEntry> legend = Map.of(
                'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                'B', new LegendEntry(BlockId.of("minecraft:dirt"), Map.of(), false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(List.of(List.of("AAB")), legend, null))
                .withMessageContaining("must appear exactly once in layers, found 2");
    }

    // ----- filler requirement -----

    @Test
    @DisplayName("ENSURE_SOLID used with a null filler throws")
    void ensureSolidWithoutFillerThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StructurePattern(List.of(List.of("A#")), SINGLE_ANCHOR, null))
                .withMessageContaining("filler must not be null when '#' is used");
    }

    @Test
    @DisplayName("ENSURE_SOLID used with a non-null filler is valid")
    void ensureSolidWithFillerSucceeds() {
        StructurePattern pattern = new StructurePattern(
                List.of(List.of("A#")), SINGLE_ANCHOR, BlockId.of("minecraft:sandstone"));

        assertThat(pattern.filler()).isEqualTo(BlockId.of("minecraft:sandstone"));
    }

    // ----- geometry helpers -----

    @Test
    @DisplayName("height/depth/width report the grid dimensions")
    void dimensionsReportGridSize() {
        StructurePattern pattern = new StructurePattern(
                List.of(List.of("###", "###"), List.of(" A ", " B.")),
                Map.of(
                        'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                        'B', new LegendEntry(BlockId.of("minecraft:water"), Map.of(), false)),
                BlockId.of("minecraft:sandstone"));

        assertThat(pattern.height()).isEqualTo(2); // 2 Y layers
        assertThat(pattern.depth()).isEqualTo(2);  // 2 Z rows per layer
        assertThat(pattern.width()).isEqualTo(3);  // 3 X columns per row
    }

    @Test
    @DisplayName("anchorOffset returns the anchor's own grid indices")
    void anchorOffsetReturnsAnchorIndices() {
        // layer 0 (bottom): "###" / "###"; layer 1 (top): " A " / " B."
        StructurePattern pattern = new StructurePattern(
                List.of(List.of("###", "###"), List.of(" A ", " B.")),
                Map.of(
                        'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                        'B', new LegendEntry(BlockId.of("minecraft:water"), Map.of(), false)),
                BlockId.of("minecraft:sandstone"));

        // Verified empirically against the real StructurePattern: the anchor 'A' sits at
        // layer 1, row 0, col 1.
        assertThat(pattern.anchorOffset()).containsExactly(1, 0, 1);
    }

    @Test
    @DisplayName("cells() enumerates every grid cell in layer-then-row-then-column order")
    void cellsEnumeratesEveryCellInOrder() {
        StructurePattern pattern = new StructurePattern(
                List.of(List.of("###", "###"), List.of(" A ", " B.")),
                Map.of(
                        'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                        'B', new LegendEntry(BlockId.of("minecraft:water"), Map.of(), false)),
                BlockId.of("minecraft:sandstone"));

        List<StructurePattern.Cell> cells = pattern.cells();

        // 2 layers x 2 rows x 3 cols = 12 cells, verified empirically.
        assertThat(cells).hasSize(12);
        assertThat(cells.get(0)).isEqualTo(new StructurePattern.Cell(0, 0, 0, '#'));
        assertThat(cells.get(7)).isEqualTo(new StructurePattern.Cell(1, 0, 1, 'A'));
        assertThat(cells.get(10)).isEqualTo(new StructurePattern.Cell(1, 1, 1, 'B'));
        assertThat(cells.get(11)).isEqualTo(new StructurePattern.Cell(1, 1, 2, '.'));
    }

    @Test
    @DisplayName("Cell.offsetFrom computes the world-axis offset from the anchor's grid indices")
    void offsetFromComputesWorldAxisOffset() {
        StructurePattern pattern = new StructurePattern(
                List.of(List.of("###", "###"), List.of(" A ", " B.")),
                Map.of(
                        'A', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                        'B', new LegendEntry(BlockId.of("minecraft:water"), Map.of(), false)),
                BlockId.of("minecraft:sandstone"));

        int[] anchor = pattern.anchorOffset();
        // Verified empirically: the anchor cell itself offsets to {0,0,0}.
        StructurePattern.Cell anchorCell = new StructurePattern.Cell(1, 0, 1, 'A');
        assertThat(anchorCell.offsetFrom(anchor)).containsExactly(0, 0, 0);

        // Verified empirically: layer 0 (one Y below anchor), row 0, col 0 offsets to {-1,-1,0}.
        StructurePattern.Cell farCorner = new StructurePattern.Cell(0, 0, 0, '#');
        assertThat(farCorner.offsetFrom(anchor)).containsExactly(-1, -1, 0);

        // Verified empirically: layer 1, row 1, col 2 ('.') offsets to {1,0,1}.
        StructurePattern.Cell clearCell = new StructurePattern.Cell(1, 1, 2, '.');
        assertThat(clearCell.offsetFrom(anchor)).containsExactly(1, 0, 1);
    }

    // ----- legendEntry lookup -----

    @Test
    @DisplayName("legendEntry returns the entry for a known symbol, empty for reserved or unknown ones")
    void legendEntryLookup() {
        StructurePattern pattern = new StructurePattern(List.of(List.of("A")), SINGLE_ANCHOR, null);

        assertThat(pattern.legendEntry('A')).contains(SINGLE_ANCHOR.get('A'));
        assertThat(pattern.legendEntry(StructurePattern.IGNORE)).isEmpty();
        assertThat(pattern.legendEntry('Z')).isEmpty();
    }
}

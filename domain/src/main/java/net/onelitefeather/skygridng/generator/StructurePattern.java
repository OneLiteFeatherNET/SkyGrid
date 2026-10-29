package net.onelitefeather.skygridng.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A declarative 3D footprint placed around a decoration's anchor cell, so a rule can guarantee real
 * solid support at generation time (fixing the class of bug where a decoration is grown on an
 * unsupported gravity block that only falls later, on the first genuine block update). Inspired by
 * Patchouli's multiblock JSON and WorldEdit's mask vocabulary.
 *
 * <p>{@code layers} is a 3D grid of single-character symbols: the outer list is Y layers ordered
 * bottom-to-top, each layer is a list of rows along the Z axis, and each row is a string of symbols
 * along the X axis. Every layer must have the same number of rows, and every row (in every layer) must
 * have the same length, so the whole grid is a uniform box.
 *
 * <p>Three symbols are reserved and fixed, never overridable by {@code legend}:
 * <ul>
 *   <li>{@link #IGNORE} (space): leave the cell exactly as generation already left it.</li>
 *   <li>{@link #ENSURE_SOLID} (#): if the cell is currently air, fill it with {@link #filler()};
 *       otherwise leave the existing terrain alone.</li>
 *   <li>{@link #CLEAR_AIR} (.): clear the cell (force air).</li>
 * </ul>
 * Every other symbol appearing anywhere in {@code layers} must have a matching entry in
 * {@code legend}, mapping it to a block (and optional state overrides). Exactly one legend entry must
 * be marked {@link LegendEntry#anchor()}, and its symbol must appear exactly once across the whole
 * grid: that cell is the pattern's anchor, the reference point every other cell is placed relative to.
 *
 * @param layers the Y-bottom-to-top list of Z-row lists of X-symbol strings (never empty; a uniform box)
 * @param legend the non-reserved symbols used in {@code layers}, mapped to their meaning (immutable;
 *               never {@code null} as a field value, though it may be empty when {@code layers} uses
 *               only reserved symbols other than the mandatory anchor, which is never the case since an
 *               anchor symbol is always a legend entry)
 * @param filler the block used to fill an {@link #ENSURE_SOLID} cell that is currently air; may be
 *               {@code null} only when {@link #ENSURE_SOLID} does not appear anywhere in {@code layers}
 */
public record StructurePattern(
        List<List<String>> layers,
        Map<Character, LegendEntry> legend,
        BlockId filler
) {

    /** Reserved symbol: leave the cell exactly as generation already left it. */
    public static final char IGNORE = ' ';

    /** Reserved symbol: ensure a solid cell, filling with {@link #filler()} only when currently air. */
    public static final char ENSURE_SOLID = '#';

    /** Reserved symbol: clear the cell (force air). */
    public static final char CLEAR_AIR = '.';

    public StructurePattern {
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException("StructurePattern.layers must not be empty");
        }
        legend = legend == null ? Map.of() : Map.copyOf(legend);
        for (Character symbol : legend.keySet()) {
            if (isReserved(symbol)) {
                throw new IllegalArgumentException(
                        "StructurePattern.legend must not redefine reserved symbol '" + symbol + "'");
            }
        }

        List<String> firstLayer = layers.get(0);
        if (firstLayer == null || firstLayer.isEmpty()) {
            throw new IllegalArgumentException("StructurePattern layer 0 must have at least one row");
        }
        int rowCount = firstLayer.size();
        String firstRow = firstLayer.get(0);
        if (firstRow == null || firstRow.isEmpty()) {
            throw new IllegalArgumentException("StructurePattern layer 0 row 0 must not be empty");
        }
        int rowLength = firstRow.length();

        List<List<String>> normalizedLayers = new ArrayList<>(layers.size());
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<String> layer = layers.get(layerIndex);
            if (layer == null || layer.isEmpty()) {
                throw new IllegalArgumentException(
                        "StructurePattern layer " + layerIndex + " must have at least one row");
            }
            if (layer.size() != rowCount) {
                throw new IllegalArgumentException(
                        "StructurePattern layer " + layerIndex + " has " + layer.size()
                                + " rows, expected " + rowCount + " (every layer must have the same number "
                                + "of rows)");
            }
            for (int rowIndex = 0; rowIndex < layer.size(); rowIndex++) {
                String row = layer.get(rowIndex);
                if (row == null) {
                    throw new IllegalArgumentException(
                            "StructurePattern layer " + layerIndex + " row " + rowIndex + " must not be null");
                }
                if (row.length() != rowLength) {
                    throw new IllegalArgumentException(
                            "StructurePattern layer " + layerIndex + " row " + rowIndex + " has length "
                                    + row.length() + ", expected " + rowLength
                                    + " (every row must be the same length)");
                }
            }
            normalizedLayers.add(List.copyOf(layer));
        }
        layers = List.copyOf(normalizedLayers);

        char anchorSymbol = 0;
        boolean anchorSymbolFound = false;
        for (Map.Entry<Character, LegendEntry> entry : legend.entrySet()) {
            if (entry.getValue().anchor()) {
                if (anchorSymbolFound) {
                    throw new IllegalArgumentException(
                            "StructurePattern.legend has more than one anchor entry: '" + anchorSymbol
                                    + "' and '" + entry.getKey() + "'");
                }
                anchorSymbol = entry.getKey();
                anchorSymbolFound = true;
            }
        }
        if (!anchorSymbolFound) {
            throw new IllegalArgumentException(
                    "StructurePattern.legend must mark exactly one entry as anchor");
        }

        boolean usesEnsureSolid = false;
        int anchorCount = 0;
        for (List<String> layer : layers) {
            for (String row : layer) {
                for (int col = 0; col < row.length(); col++) {
                    char symbol = row.charAt(col);
                    if (symbol == ENSURE_SOLID) {
                        usesEnsureSolid = true;
                    }
                    if (isReserved(symbol)) {
                        continue;
                    }
                    if (!legend.containsKey(symbol)) {
                        throw new IllegalArgumentException(
                                "StructurePattern uses unknown symbol '" + symbol
                                        + "' with no matching legend entry");
                    }
                    if (symbol == anchorSymbol) {
                        anchorCount++;
                    }
                }
            }
        }
        if (anchorCount != 1) {
            throw new IllegalArgumentException(
                    "StructurePattern anchor symbol '" + anchorSymbol + "' must appear exactly once in "
                            + "layers, found " + anchorCount);
        }
        if (usesEnsureSolid && filler == null) {
            throw new IllegalArgumentException(
                    "StructurePattern.filler must not be null when '" + ENSURE_SOLID + "' is used in layers");
        }
    }

    private static boolean isReserved(char symbol) {
        return symbol == IGNORE || symbol == ENSURE_SOLID || symbol == CLEAR_AIR;
    }

    /** Number of Y layers, bottom-to-top. */
    public int height() {
        return layers.size();
    }

    /** Number of Z rows, uniform across every layer. */
    public int depth() {
        return layers.get(0).size();
    }

    /** Number of X columns, uniform across every row of every layer. */
    public int width() {
        return layers.get(0).get(0).length();
    }

    /**
     * One cell of the grid: its zero-based grid indices (Y layer bottom-to-top, Z row, X column) and
     * its raw symbol (a reserved symbol, or a key of {@link #legend()}).
     */
    public record Cell(int layer, int row, int col, char symbol) {

        /**
         * This cell's offset from the anchor cell in world axes, {@code {dx, dy, dz}}: {@code dx} is
         * the X (column) offset, {@code dy} is the Y (layer) offset, and {@code dz} is the Z (row)
         * offset. Add this to the anchor's world position to get this cell's world position.
         *
         * @param anchor the anchor's own grid indices, as returned by {@link #anchorOffset()}
         */
        public int[] offsetFrom(int[] anchor) {
            return new int[] {col - anchor[2], layer - anchor[0], row - anchor[1]};
        }
    }

    /** Every cell of the grid, in layer-then-row-then-column order (an immutable list). */
    public List<Cell> cells() {
        List<Cell> result = new ArrayList<>();
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<String> layer = layers.get(layerIndex);
            for (int rowIndex = 0; rowIndex < layer.size(); rowIndex++) {
                String row = layer.get(rowIndex);
                for (int col = 0; col < row.length(); col++) {
                    result.add(new Cell(layerIndex, rowIndex, col, row.charAt(col)));
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * The anchor cell's own grid indices, {@code {layer, row, col}} (zero-based: layer 0 is the
     * bottommost layer). Combined with {@link Cell#offsetFrom(int[])}, this lets a caller compute, for
     * any cell, its offset from the anchor's world position: given the anchor's world position
     * {@code (ax, ay, az)} and a cell's {@code offsetFrom(anchorOffset())} of {@code {dx, dy, dz}}, that
     * cell's world position is {@code (ax + dx, ay + dy, az + dz)}.
     */
    public int[] anchorOffset() {
        char anchorSymbol = anchorSymbol();
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<String> layer = layers.get(layerIndex);
            for (int rowIndex = 0; rowIndex < layer.size(); rowIndex++) {
                int col = layer.get(rowIndex).indexOf(anchorSymbol);
                if (col >= 0) {
                    return new int[] {layerIndex, rowIndex, col};
                }
            }
        }
        throw new IllegalStateException(
                "StructurePattern anchor symbol '" + anchorSymbol + "' not found, unreachable after validation");
    }

    /** The legend entry for {@code symbol}, if any (empty for a reserved symbol or an unknown one). */
    public Optional<LegendEntry> legendEntry(char symbol) {
        return Optional.ofNullable(legend.get(symbol));
    }

    private char anchorSymbol() {
        for (Map.Entry<Character, LegendEntry> entry : legend.entrySet()) {
            if (entry.getValue().anchor()) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("StructurePattern has no anchor entry, unreachable after validation");
    }
}

package net.onelitefeather.skygridng.adapters.generator;

import net.onelitefeather.skygridng.api.model.LegendEntry;
import net.onelitefeather.skygridng.api.model.StructurePattern;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.LimitedRegion;

/**
 * Places a {@link StructurePattern} around a decoration's anchor cell, guaranteeing the solid support
 * (and any boxed water, walls, or clearance) the pattern declares actually exists at generation time,
 * instead of relying on a later block update to notice a floating gravity block.
 *
 * <p>{@code (x, y, z)} is the world position of the pattern's anchor cell, the same cell
 * {@code requiresGround} targeted before this replaced it. Every other cell's world position is the
 * anchor position plus that cell's {@link StructurePattern.Cell#offsetFrom(int[])}.
 */
final class StructurePlacer {

    private StructurePlacer() {
    }

    /**
     * Applies {@code pattern} anchored at {@code (x, y, z)}. First checks that every cell the pattern
     * would actually touch (everything but {@link StructurePattern#IGNORE}) fits inside {@code area};
     * if any does not, places nothing and returns {@code false} so the caller can consume the roll
     * without a half-placed structure (the same contract {@link StillWater#place} has). Only then does
     * it place: {@link StructurePattern#CLEAR_AIR} clears the cell, {@link StructurePattern#ENSURE_SOLID}
     * fills an air cell with the pattern's {@code filler} and leaves existing terrain alone, and every
     * other symbol resolves its legend entry to block data and places it.
     */
    static boolean place(LimitedRegion area, RuleResolver rules, StructurePattern pattern, int x, int y, int z) {
        int[] anchor = pattern.anchorOffset();
        for (StructurePattern.Cell cell : pattern.cells()) {
            if (cell.symbol() == StructurePattern.IGNORE) {
                continue;
            }
            int[] offset = cell.offsetFrom(anchor);
            if (!area.isInRegion(x + offset[0], y + offset[1], z + offset[2])) {
                return false;
            }
        }
        for (StructurePattern.Cell cell : pattern.cells()) {
            char symbol = cell.symbol();
            if (symbol == StructurePattern.IGNORE) {
                continue;
            }
            int[] offset = cell.offsetFrom(anchor);
            int wx = x + offset[0];
            int wy = y + offset[1];
            int wz = z + offset[2];
            if (symbol == StructurePattern.CLEAR_AIR) {
                area.setType(wx, wy, wz, Material.AIR);
            } else if (symbol == StructurePattern.ENSURE_SOLID) {
                if (area.getType(wx, wy, wz) == Material.AIR) {
                    area.setBlockData(wx, wy, wz, rules.blockData(pattern.filler()));
                }
            } else {
                LegendEntry entry = pattern.legendEntry(symbol)
                        .orElseThrow(() -> new IllegalStateException(
                                "StructurePattern uses symbol '" + symbol + "' with no legend entry, "
                                        + "unreachable after validation"));
                BlockData data = rules.blockData(entry.block(), entry.state());
                area.setBlockData(wx, wy, wz, data);
            }
        }
        return true;
    }
}

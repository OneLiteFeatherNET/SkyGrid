package net.onelitefeather.gridwfc.core.port.in;

import net.onelitefeather.gridwfc.core.model.RuleSet;
import net.onelitefeather.gridwfc.core.model.TileType;

/**
 * In port: what the outside world (Paper generator and populator) needs from the WFC core.
 *
 * Adapters know only this interface, not the implementation. This keeps the engine swappable and
 * testable without Bukkit.
 */
public interface GenerateRegionUseCase {

    /** Returns the deterministically chosen tile type of a global cell. */
    TileType tileAt(long worldSeed, int cellX, int cellZ);

    /** Current WFC rule snapshot (for block and material mapping in the adapter). */
    RuleSet rules();
}

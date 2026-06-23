package net.onelitefeather.skygridng.core.model;

/**
 * Global WFC generation parameters, data driven from JSON.
 *
 * @param cellSize          blocks per WFC cell (for example 4)
 * @param regionSize        cells per region edge (cache granularity, seam density)
 * @param surfaceY          fixed surface and water level Y
 * @param maxSolverAttempts restarts on contradiction before the fallback kicks in
 * @param fallbackTileId    tile id used for the fallback fill and the seam anchor
 * @param allowSelf         may tiles sit next to themselves (homogeneous fields)
 */
public record GenerationSettings(
        int cellSize,
        int regionSize,
        int surfaceY,
        int maxSolverAttempts,
        String fallbackTileId,
        boolean allowSelf
) {
    public GenerationSettings {
        if (cellSize < 1) {
            throw new IllegalArgumentException("cellSize must be >= 1");
        }
        if (regionSize < 2) {
            throw new IllegalArgumentException("regionSize must be >= 2");
        }
        if (maxSolverAttempts < 1) {
            throw new IllegalArgumentException("maxSolverAttempts must be >= 1");
        }
        if (fallbackTileId == null || fallbackTileId.isBlank()) {
            throw new IllegalArgumentException("fallbackTileId must not be blank");
        }
    }
}

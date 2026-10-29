package net.onelitefeather.skygridng.generator;


import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A hand-written, in-memory {@link GroundReader} test double, in the same spirit as
 * {@code FakeRegionWriter} in {@code generator}: this module is framework-free by
 * architecture rule (no Bukkit, no MockBukkit), so a plain map-backed fake is easier to reason
 * about than stubbing a mock's defaults in every test.
 *
 * <p>Every column defaults to a void column: {@link #highestGroundY} returns {@code OptionalInt.empty()}
 * unless a ground y is preset for that column via {@link #presetGround}. Every coordinate defaults to
 * {@code minecraft:air}, solid {@code false}, passable {@code true}, unless overridden via
 * {@link #presetBlock}.
 */
final class FakeGroundReader implements GroundReader {

    private static final BlockId AIR = BlockId.of("minecraft:air");

    private final Map<Column, Integer> groundY = new HashMap<>();
    private final Map<Coord, BlockId> blocks = new HashMap<>();
    private final Map<Coord, Boolean> solid = new HashMap<>();
    private final Map<Coord, Boolean> passable = new HashMap<>();

    /** Presets the highest ground y for column (x, z); omit to keep it a void column. */
    void presetGround(int x, int z, int y) {
        groundY.put(new Column(x, z), y);
    }

    /** Presets the block, solidity, and passability at (x, y, z). */
    void presetBlock(int x, int y, int z, BlockId block, boolean isSolid, boolean isPassable) {
        Coord coord = new Coord(x, y, z);
        blocks.put(coord, block);
        solid.put(coord, isSolid);
        passable.put(coord, isPassable);
    }

    /** Convenience for a whole safe column: solid, safe ground at {@code y}, open air above it. */
    void safeColumn(int x, int z, int y, BlockId groundBlock) {
        presetGround(x, z, y);
        presetBlock(x, y, z, groundBlock, true, false);
        presetBlock(x, y + 1, z, AIR, false, true);
        presetBlock(x, y + 2, z, AIR, false, true);
    }

    @Override
    public OptionalInt highestGroundY(int x, int z) {
        Integer y = groundY.get(new Column(x, z));
        return y == null ? OptionalInt.empty() : OptionalInt.of(y);
    }

    @Override
    public BlockId blockAt(int x, int y, int z) {
        return blocks.getOrDefault(new Coord(x, y, z), AIR);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return solid.getOrDefault(new Coord(x, y, z), false);
    }

    @Override
    public boolean isPassable(int x, int y, int z) {
        return passable.getOrDefault(new Coord(x, y, z), true);
    }

    record Column(int x, int z) {
    }

    record Coord(int x, int y, int z) {
    }
}

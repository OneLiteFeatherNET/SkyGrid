package net.onelitefeather.skygridng.generator;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A hand-written, in-memory {@link RegionWriter} test double shared by every test in this package.
 * This module is framework-free by architecture rule (no Bukkit, no MockBukkit), so this is a plain
 * fake rather than a Mockito mock: it is easier to reason about a map-backed default (everything is
 * in-region, every block defaults to {@code minecraft:air}) than to stub the same defaults on a mock
 * for every test.
 *
 * <p>Every coordinate is in-region and reads {@code minecraft:air} unless configured otherwise via
 * {@link #excludeFromRegion} or {@link #presetBlock}. Every call is recorded, so a test can assert not
 * only the end state (via {@link #blockAt}) but also exactly which coordinates were read or written,
 * matching the "never reads/writes an IGNORE cell" style of assertion the old MockBukkit-based tests
 * made with {@code verify(mock, never())...}.
 */
final class FakeRegionWriter implements RegionWriter {

    private static final BlockId AIR = BlockId.of("minecraft:air");

    private final Set<Coord> outOfRegion = new HashSet<>();
    private final Map<Coord, BlockId> blocks = new HashMap<>();
    private final Map<Coord, Map<String, String>> states = new HashMap<>();

    private final List<Coord> isInRegionCalls = new ArrayList<>();
    private final List<Coord> blockAtCalls = new ArrayList<>();
    private final List<Placement> placements = new ArrayList<>();
    private final List<ScheduledUpdate> scheduledUpdates = new ArrayList<>();
    private final List<LootFill> lootFills = new ArrayList<>();
    private final List<SpawnerFill> spawnerFills = new ArrayList<>();

    /** Marks (x, y, z) as outside the region this writer can read or write. */
    void excludeFromRegion(int x, int y, int z) {
        outOfRegion.add(new Coord(x, y, z));
    }

    /** Presets the block at (x, y, z), read back by {@link #blockAt} until something places over it. */
    void presetBlock(int x, int y, int z, BlockId block) {
        blocks.put(new Coord(x, y, z), block);
    }

    @Override
    public boolean isInRegion(int x, int y, int z) {
        isInRegionCalls.add(new Coord(x, y, z));
        return !outOfRegion.contains(new Coord(x, y, z));
    }

    @Override
    public BlockId blockAt(int x, int y, int z) {
        blockAtCalls.add(new Coord(x, y, z));
        return blocks.getOrDefault(new Coord(x, y, z), AIR);
    }

    @Override
    public void place(int x, int y, int z, BlockId block) {
        place(x, y, z, block, Map.of());
    }

    @Override
    public void place(int x, int y, int z, BlockId block, Map<String, String> state) {
        Coord coord = new Coord(x, y, z);
        blocks.put(coord, block);
        states.put(coord, Map.copyOf(state));
        placements.add(new Placement(x, y, z, block, Map.copyOf(state)));
    }

    @Override
    public void scheduleUpdate(int x, int y, int z, PhysicsMode mode) {
        scheduledUpdates.add(new ScheduledUpdate(x, y, z, mode));
    }

    @Override
    public void fillLootChest(int x, int y, int z, BlockId lootTable, long seed) {
        lootFills.add(new LootFill(x, y, z, lootTable, seed));
    }

    @Override
    public void fillSpawner(int x, int y, int z, BlockId mobType) {
        spawnerFills.add(new SpawnerFill(x, y, z, mobType));
    }

    /** State applied at (x, y, z) by the state-map overload of {@link #place}, or empty if never called. */
    Map<String, String> stateAt(int x, int y, int z) {
        return states.getOrDefault(new Coord(x, y, z), Map.of());
    }

    List<Coord> isInRegionCalls() {
        return List.copyOf(isInRegionCalls);
    }

    List<Coord> blockAtCalls() {
        return List.copyOf(blockAtCalls);
    }

    List<Placement> placements() {
        return List.copyOf(placements);
    }

    List<ScheduledUpdate> scheduledUpdates() {
        return List.copyOf(scheduledUpdates);
    }

    List<LootFill> lootFills() {
        return List.copyOf(lootFills);
    }

    List<SpawnerFill> spawnerFills() {
        return List.copyOf(spawnerFills);
    }

    record Coord(int x, int y, int z) {
    }

    record Placement(int x, int y, int z, BlockId block, Map<String, String> state) {
    }

    record ScheduledUpdate(int x, int y, int z, PhysicsMode mode) {
    }

    record LootFill(int x, int y, int z, BlockId lootTable, long seed) {
    }

    record SpawnerFill(int x, int y, int z, BlockId mobType) {
    }
}

package net.onelitefeather.skygridng.generator;


/**
 * Lays spawn slots out on an outward square spiral so the n-th player to join lands {@code spacing}
 * blocks from every other player: ordinal 0 sits at the origin, then the slots wind outward one
 * grid cell at a time. Because grid cells never repeat, any two distinct ordinals are at least one
 * spacing apart, which keeps players out of each other's view on a shared world.
 *
 * Pure and platform-neutral: the live {@link SpawnConfig} is read from {@link RuleState}, so a
 * runtime config change takes effect on the next join.
 */
public final class SpiralSpawnService implements SpawnPoints {

    private final RuleState state;

    public SpiralSpawnService(RuleState state) {
        this.state = state;
    }

    private SpawnConfig config() {
        return state.current().spawn();
    }

    @Override
    public boolean spread() {
        return config().spread();
    }

    @Override
    public int searchRadius() {
        return config().searchRadius();
    }

    @Override
    public int maxAttempts() {
        return config().maxAttempts();
    }

    @Override
    public SpawnPoint at(int ordinal) {
        int[] cell = spiralCell(Math.max(0, ordinal));
        int spacing = config().spacing();
        return new SpawnPoint(cell[0] * spacing, cell[1] * spacing);
    }

    /**
     * Walks a square spiral and returns the grid cell {@code [x, z]} for the given index: index 0 is
     * the origin, and each step moves to the next cell, turning to spiral outward (0,0 -> 1,0 -> 1,1
     * -> 0,1 -> -1,1 -> ...). O(index), which is fine for the small number of players on a server.
     */
    static int[] spiralCell(int index) {
        int x = 0;
        int z = 0;
        int dx = 1;
        int dz = 0;
        int segmentLength = 1;
        int stepsInSegment = 0;
        int turns = 0;
        for (int i = 0; i < index; i++) {
            x += dx;
            z += dz;
            if (++stepsInSegment == segmentLength) {
                stepsInSegment = 0;
                int t = dx;          // rotate the heading 90 degrees
                dx = -dz;
                dz = t;
                if (++turns % 2 == 0) {
                    segmentLength++; // the spiral arm grows every other turn
                }
            }
        }
        return new int[] {x, z};
    }
}

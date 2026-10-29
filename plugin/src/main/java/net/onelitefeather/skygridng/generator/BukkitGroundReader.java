package net.onelitefeather.skygridng.generator;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.OptionalInt;

/**
 * {@link GroundReader} implementation wrapping a live Paper {@link World}: the seam that lets the
 * framework-free first-join safe-spawn search in {@code adapters.spawn} run against a real world
 * without depending on Bukkit itself. Constructed fresh for each first-join event; never held as a
 * long lived or injected singleton, since the specific world it wraps only exists on the platform
 * and can change from one join to the next.
 */
final class BukkitGroundReader implements GroundReader {

    private final World world;

    BukkitGroundReader(World world) {
        this.world = world;
    }

    @Override
    public OptionalInt highestGroundY(int x, int z) {
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (groundY <= world.getMinHeight()) {
            return OptionalInt.empty(); // void column, not worth reading blocks
        }
        return OptionalInt.of(groundY);
    }

    @Override
    public BlockId blockAt(int x, int y, int z) {
        Material material = world.getBlockAt(x, y, z).getType();
        return BlockId.of(material.getKey().asString());
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isSolid();
    }

    @Override
    public boolean isPassable(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        return block.isPassable();
    }
}

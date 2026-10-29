package net.onelitefeather.skygridng.season;

import org.bukkit.World;

/**
 * The world key of a season and the scope check of the challenge logic. Only the world of the active season is in
 * scope, so a prepared world that is not active yet never triggers a challenge.
 */
public final class SeasonWorldKeys {

    private static final String PREFIX = "skygridng:season_";

    private SeasonWorldKeys() {
    }

    /** The namespaced key of the world of {@code id}, for example {@code skygridng:season_s01}. */
    public static String worldKey(SeasonId id) {
        return PREFIX + id.value();
    }

    /** Whether {@code world} is in scope: the active season's world, or every world when no season is configured. */
    public static boolean isActive(World world, CurrentSeason current) {
        return current.season()
                .map(season -> worldKey(season.id()).equals(world.getKey().toString()))
                .orElse(true);
    }
}

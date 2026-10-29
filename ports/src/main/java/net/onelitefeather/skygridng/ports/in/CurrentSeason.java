package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonId;

import java.util.Optional;

/**
 * In port: the season that progress is written to right now. Progress services resolve it per call, so
 * the challenge behaviours never pass a season themselves. When no season is configured, {@link #id()}
 * is {@link SeasonId#LEGACY} and {@link #season()} is empty.
 */
public interface CurrentSeason {

    /** The id of the current season, {@link SeasonId#LEGACY} when no season is configured. */
    SeasonId id();

    /** The configured definition of the current season, empty when no season is configured. */
    Optional<Season> season();
}

package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;

import java.util.Optional;

/**
 * The {@link CurrentSeason} used until the season lifecycle exists: no season is configured, so every
 * progress write goes to {@link SeasonId#LEGACY}, the season that every pre-season row was migrated into.
 * Replaced by the season service once an active season can be resolved.
 */
public final class LegacyCurrentSeason implements CurrentSeason {

    @Override
    public SeasonId id() {
        return SeasonId.LEGACY;
    }

    @Override
    public Optional<Season> season() {
        return Optional.empty();
    }
}

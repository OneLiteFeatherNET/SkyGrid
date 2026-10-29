package net.onelitefeather.skygridng.season;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * All configured seasons, ordered by their start instant. An empty catalog means no season is
 * configured, so the implicit {@link SeasonId#LEGACY} season runs and no transition is scheduled.
 *
 * @param seasons the seasons, sorted by start (the constructor sorts them); ids must be unique
 */
public record SeasonCatalog(List<Season> seasons) {

    public SeasonCatalog {
        seasons = seasons == null
                ? List.of()
                : seasons.stream().sorted(Comparator.comparing(Season::start)).toList();
        Set<SeasonId> ids = new HashSet<>();
        for (Season season : seasons) {
            if (!ids.add(season.id())) {
                throw new IllegalArgumentException("duplicate season id " + season.id().value());
            }
        }
    }

    /** A catalog with no seasons. */
    public static SeasonCatalog empty() {
        return new SeasonCatalog(List.of());
    }

    /** The season whose window contains {@code instant}, if any. */
    public Optional<Season> at(Instant instant) {
        return seasons.stream().filter(season -> season.contains(instant)).findFirst();
    }

    /** The season that starts after the season with {@code id}, if there is one. */
    public Optional<Season> next(SeasonId id) {
        int index = -1;
        for (int i = 0; i < seasons.size(); i++) {
            if (seasons.get(i).id().equals(id)) {
                index = i;
                break;
            }
        }
        return index < 0 || index + 1 >= seasons.size() ? Optional.empty() : Optional.of(seasons.get(index + 1));
    }
}

package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.out.SeasonRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure season rules that need no clock, no file and no database: validating the configured seasons against
 * the known presets and challenges, rejecting overlapping windows, applying a confirmed reload to future
 * seasons only, and answering which season is active at an instant. Every instant is passed in.
 */
public final class SeasonTimeline {

    private SeasonTimeline() {
    }

    /**
     * The outcome of a validation: the seasons that were accepted, and one log line per rejected season.
     *
     * @param accepted   the accepted seasons, ordered by start
     * @param rejections a human readable reason for each rejected season, never {@code null}
     */
    public record Validation(SeasonCatalog accepted, List<String> rejections) {
        public Validation {
            accepted = accepted == null ? SeasonCatalog.empty() : accepted;
            rejections = List.copyOf(rejections);
        }
    }

    /**
     * Validates every candidate season. A season is rejected when it names an unknown preset or challenge,
     * or when its window overlaps a season already accepted (the later-starting one is rejected). The other
     * seasons keep loading.
     */
    public static Validation validate(SeasonCatalog candidates, Set<String> presetIds, Set<String> challengeIds) {
        return validate(candidates.seasons(), presetIds, challengeIds, List.of());
    }

    /**
     * Applies a reload of {@code seasons/}. Without {@code confirmed} nothing is read and the running catalog is
     * returned unchanged. With it, the seasons that have started by {@code now} keep their current definition,
     * and only the seasons that start after {@code now} are taken from the repository and validated.
     */
    public static Validation reload(
            SeasonCatalog running,
            SeasonRepository repository,
            Set<String> presetIds,
            Set<String> challengeIds,
            Instant now,
            boolean confirmed) {
        if (!confirmed) {
            return new Validation(running, List.of());
        }
        List<Season> started = running.seasons().stream()
                .filter(season -> !season.start().isAfter(now))
                .toList();
        List<Season> future = repository.load().seasons().stream()
                .filter(season -> season.start().isAfter(now))
                .toList();
        return validate(future, presetIds, challengeIds, started);
    }

    /**
     * The id of the season whose window contains {@code now}. When no season covers the instant, the implicit
     * {@link SeasonId#LEGACY} season is returned, which is the only season when none is configured. Gap handling
     * between configured seasons is decided by the season service, not here.
     */
    public static SeasonId activeId(SeasonCatalog catalog, Instant now) {
        return catalog.at(now).map(Season::id).orElse(SeasonId.LEGACY);
    }

    private static Validation validate(
            List<Season> candidates,
            Set<String> presetIds,
            Set<String> challengeIds,
            List<Season> fixed) {
        List<Season> accepted = new ArrayList<>(fixed);
        List<String> rejections = new ArrayList<>();
        candidates.stream()
                .sorted(Comparator.comparing(Season::start))
                .forEach(season -> {
                    String rejection = rejection(season, presetIds, challengeIds, accepted);
                    if (rejection == null) {
                        accepted.add(season);
                    } else {
                        rejections.add(rejection);
                    }
                });
        return new Validation(new SeasonCatalog(accepted), rejections);
    }

    /** The reason a season is rejected, or {@code null} when it is accepted. */
    private static String rejection(Season season, Set<String> presetIds, Set<String> challengeIds,
                                    List<Season> accepted) {
        String id = season.id().value();
        if (!presetIds.contains(season.preset())) {
            return "season '" + id + "' names unknown preset '" + season.preset()
                    + "'; known presets: " + new TreeSet<>(presetIds);
        }
        for (String challenge : season.challenges()) {
            if (!challengeIds.contains(challenge)) {
                return "season '" + id + "' names unknown challenge '" + challenge
                        + "'; known challenges: " + new TreeSet<>(challengeIds);
            }
        }
        for (Season other : accepted) {
            if (season.start().isBefore(other.end()) && other.start().isBefore(season.end())) {
                return "season '" + id + "' overlaps season '" + other.id().value() + "', so it is rejected";
            }
        }
        return null;
    }
}

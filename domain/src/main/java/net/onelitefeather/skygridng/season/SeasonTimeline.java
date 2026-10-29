package net.onelitefeather.skygridng.season;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    /** Phases a restart resumes without waiting for a clock instant. Failed phases wait for an admin retry. */
    private static final Set<SeasonPhase> RESUMABLE =
            EnumSet.of(SeasonPhase.PREPARING, SeasonPhase.CLOSING, SeasonPhase.UNLOADING, SeasonPhase.ARCHIVING);

    private static final Duration DAY = Duration.ofDays(1);

    /**
     * Decides the next action of the season lifecycle. Resumable runs come first. Otherwise the active season
     * is the one whose end is the boundary: its notices, the preparation of the next world and the switch at the
     * boundary are the candidates, and the earliest one wins. Without a next season the warning is returned,
     * once per day from {@code lastWarning}. Without an active run the first season that has not ended is the
     * next one, and the boundary is its start (see {@link #fromLegacy}).
     *
     * @param now         the instant of the decision, passed in so the method never reads the clock
     * @param catalog     the validated seasons
     * @param settings    the season settings, for the preparation lead
     * @param runs        the persisted runs (active and in flight)
     * @param sent        the notices already sent, keyed by season, kind and boundary
     * @param lastWarning when the last no-next-season warning was sent, if ever
     */
    public static SeasonAction nextAction(
            Instant now,
            SeasonCatalog catalog,
            SeasonSettings settings,
            List<SeasonRun> runs,
            Set<SeasonAction.Announce> sent,
            Optional<Instant> lastWarning) {
        Optional<SeasonRun> resumable = runs.stream().filter(run -> RESUMABLE.contains(run.phase())).findFirst();
        if (resumable.isPresent()) {
            return new SeasonAction.Resume(resumable.get());
        }
        Optional<SeasonRun> active = runs.stream().filter(run -> run.phase() == SeasonPhase.ACTIVE).findFirst();
        if (active.isEmpty()) {
            return fromLegacy(now, catalog, settings, runs, sent);
        }
        Optional<Season> ending = season(catalog, active.get().seasonId());
        if (ending.isEmpty()) {
            return new SeasonAction.None();
        }
        Instant boundary = ending.get().end();
        Optional<Season> next = catalog.next(ending.get().id());
        if (next.isEmpty()) {
            return new SeasonAction.WarnNoNext(warningAt(boundary, lastWarning));
        }
        return soonest(now, ending.get().id(), next.get(), boundary, settings, runs, sent, false);
    }

    /**
     * Without an active run the implicit {@link SeasonId#LEGACY} season runs. Its successor is the first season that
     * has not ended yet, and its boundary is that season's start. A server that starts inside that season prepares
     * and switches at once, and only the notices still ahead are returned.
     */
    private static SeasonAction fromLegacy(
            Instant now,
            SeasonCatalog catalog,
            SeasonSettings settings,
            List<SeasonRun> runs,
            Set<SeasonAction.Announce> sent) {
        Optional<Season> first = catalog.seasons().stream().filter(season -> season.end().isAfter(now)).findFirst();
        if (first.isEmpty()) {
            return new SeasonAction.None();
        }
        return soonest(now, SeasonId.LEGACY, first.get(), first.get().start(), settings, runs, sent, true);
    }

    /**
     * The earliest candidate of the boundary {@code boundary} between {@code ending} and {@code incoming}: its unsent
     * notices, then the preparation of the incoming world when no run or only a planned run exists, or the switch
     * once the incoming world is prepared. Notices whose instant has already passed are skipped when {@code late}.
     */
    private static SeasonAction soonest(
            Instant now,
            SeasonId ending,
            Season incoming,
            Instant boundary,
            SeasonSettings settings,
            List<SeasonRun> runs,
            Set<SeasonAction.Announce> sent,
            boolean late) {
        List<SeasonAction> candidates = new ArrayList<>();
        for (SeasonAction.AnnouncementKind kind : SeasonAction.AnnouncementKind.values()) {
            SeasonAction.Announce notice =
                    new SeasonAction.Announce(incoming.id(), kind, boundary, boundary.minus(kind.offset()));
            if (!sent.contains(notice) && (!late || !notice.at().isBefore(now))) {
                candidates.add(notice);
            }
        }
        Optional<SeasonRun> incomingRun = runs.stream()
                .filter(run -> run.seasonId().equals(incoming.id()))
                .findFirst();
        if (incomingRun.isEmpty() || incomingRun.get().phase() == SeasonPhase.PLANNED) {
            candidates.add(new SeasonAction.Prepare(incoming, boundary.minus(settings.prepareLead())));
        } else if (incomingRun.get().phase() == SeasonPhase.PREPARED) {
            candidates.add(new SeasonAction.Switch(ending, incoming.id(), boundary));
        }
        return candidates.stream()
                .min(Comparator.comparing(SeasonTimeline::at))
                .map(SeasonAction.class::cast)
                .orElse(new SeasonAction.None());
    }

    /** Whether {@code action} is due at {@code now}: its instant has passed, a resume is always due. */
    public static boolean isDue(SeasonAction action, Instant now) {
        return switch (action) {
            case SeasonAction.None none -> false;
            case SeasonAction.Resume resume -> true;
            case SeasonAction.Announce announce -> !announce.at().isAfter(now);
            case SeasonAction.Prepare prepare -> !prepare.at().isAfter(now);
            case SeasonAction.Switch clock -> !clock.at().isAfter(now);
            case SeasonAction.WarnNoNext warning -> !warning.at().isAfter(now);
        };
    }

    private static Instant at(SeasonAction action) {
        return switch (action) {
            case SeasonAction.Announce announce -> announce.at();
            case SeasonAction.Prepare prepare -> prepare.at();
            case SeasonAction.Switch clock -> clock.at();
            case SeasonAction.WarnNoNext warning -> warning.at();
            case SeasonAction.None none -> Instant.MAX;
            case SeasonAction.Resume resume -> Instant.MIN;
        };
    }

    private static Instant warningAt(Instant boundary, Optional<Instant> lastWarning) {
        return lastWarning.map(last -> last.plus(DAY))
                .filter(next -> next.isAfter(boundary))
                .orElse(boundary);
    }

    private static Optional<Season> season(SeasonCatalog catalog, SeasonId id) {
        return catalog.seasons().stream().filter(season -> season.id().equals(id)).findFirst();
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

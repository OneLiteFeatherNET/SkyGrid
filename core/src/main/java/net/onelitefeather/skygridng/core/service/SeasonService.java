package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonPhase;
import net.onelitefeather.skygridng.api.model.SeasonRun;
import net.onelitefeather.skygridng.api.model.SeasonSettings;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import net.onelitefeather.skygridng.ports.in.SeasonAdminUseCase;
import net.onelitefeather.skygridng.ports.out.PlayerSeasonReset;
import net.onelitefeather.skygridng.ports.out.SeasonAnnouncer;
import net.onelitefeather.skygridng.ports.out.SeasonRunRepository;
import net.onelitefeather.skygridng.ports.out.SeasonTransitionListener;
import net.onelitefeather.skygridng.ports.out.SeasonWorlds;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The season lifecycle in the core: the scheduler entry point, the admin operations and the {@link CurrentSeason}
 * that progress is written to. One scheduler tick runs at most one due step, and a single-flight guard refuses any
 * call that arrives while a step is running. The active season id is cached and refreshed on every switch, so
 * progress writes do not read the database. Transition listeners run after the switch is committed; a failing
 * listener is logged and alerted, and the remaining listeners still run.
 *
 * <p>The effective end of the active season and the notices already sent are persisted on its run, so a restart
 * neither loses a postponement nor repeats a notice. Each decision reads them back first, so the constructor never
 * touches the database. The last no-next-season warning is kept in memory: a restart may repeat that warning.
 */
public final class SeasonService implements SeasonAdminUseCase, CurrentSeason {

    private static final Logger LOGGER = Logger.getLogger(SeasonService.class.getName());

    private static final Set<SeasonPhase> RUNNING =
            Set.of(SeasonPhase.PREPARING, SeasonPhase.CLOSING, SeasonPhase.UNLOADING, SeasonPhase.ARCHIVING);

    private SeasonCatalog catalog;
    private final SeasonSettings settings;
    private final SeasonRunRepository runs;
    private final SeasonWorlds worlds;
    private final ChallengeAdminUseCase challenges;
    private final SeasonAnnouncer announcer;
    private final PlayerSeasonReset resets;
    private final Set<SeasonTransitionListener> listeners;
    private final LongSupplier seeds;
    private Optional<Instant> lastWarning = Optional.empty();
    private SeasonId activeId = SeasonId.LEGACY;
    private boolean busy;

    public SeasonService(
            SeasonCatalog catalog,
            SeasonSettings settings,
            SeasonRunRepository runs,
            SeasonWorlds worlds,
            ChallengeAdminUseCase challenges,
            SeasonAnnouncer announcer,
            PlayerSeasonReset resets,
            Set<SeasonTransitionListener> listeners,
            LongSupplier seeds) {
        this.catalog = catalog;
        this.settings = settings;
        this.runs = runs;
        this.worlds = worlds;
        this.challenges = challenges;
        this.announcer = announcer;
        this.resets = resets;
        this.listeners = listeners;
        this.seeds = seeds;
    }

    @Override
    public SeasonId id() {
        return activeId;
    }

    @Override
    public Optional<Season> season() {
        return catalog.seasons().stream().filter(season -> season.id().equals(activeId)).findFirst();
    }

    @Override
    public SeasonCatalog list() {
        return catalog;
    }

    @Override
    public boolean tick(Instant now) {
        return guarded("tick", () -> {
            Optional<SeasonRun> active = reconcile();
            List<SeasonRun> known = knownRuns(active);
            SeasonAction action = known.stream()
                    .filter(run -> run.phase() == SeasonPhase.PREPARED && !worlds.isLoaded(run.worldKey()))
                    .findFirst()
                    .<SeasonAction>map(SeasonAction.Resume::new)
                    .orElseGet(() -> SeasonTimeline.nextAction(now, catalog, settings, known, sentNotices(known),
                            lastWarning));
            if (!SeasonTimeline.isDue(action, now)) {
                return false;
            }
            execute(action, now);
            return true;
        });
    }

    /**
     * Confirmed advance: the active season ends at {@code now} plus the minimum notice, so the announcements of that
     * boundary have the notice they need. Refused when no next season is configured or a transition is running.
     */
    @Override
    public boolean advance(String actor, Instant now) {
        return guarded("advance", () -> {
            Optional<Season> ending = idleActiveSeason();
            if (ending.isEmpty() || catalog.next(ending.get().id()).isEmpty()) {
                return false;
            }
            Instant boundary = now.plus(settings.advanceMinNotice());
            if (!boundary.isAfter(ending.get().start())) {
                return false;
            }
            reschedule(ending.get(), boundary);
            LOGGER.info("season advance confirmed by " + actor + " at " + now + ", boundary set to " + boundary);
            return true;
        });
    }

    /** Confirmed immediate advance: the active season ends at {@code now}. The acting admin and the instant are logged. */
    @Override
    public boolean advanceNow(String actor, Instant now) {
        return guarded("advance now", () -> {
            Optional<Season> ending = idleActiveSeason();
            if (ending.isEmpty() || catalog.next(ending.get().id()).isEmpty()
                    || !now.isAfter(ending.get().start())) {
                return false;
            }
            reschedule(ending.get(), now);
            LOGGER.info("immediate season advance confirmed by " + actor + " at " + now);
            return true;
        });
    }

    @Override
    public boolean retry(Instant now) {
        return guarded("retry", () -> {
            Optional<SeasonRun> failed = runs.inFlight().stream()
                    .filter(run -> run.phase() == SeasonPhase.PREPARE_FAILED
                            || run.phase() == SeasonPhase.ARCHIVE_FAILED)
                    .findFirst();
            if (failed.isEmpty()) {
                return false;
            }
            transition().perform(new SeasonAction.Resume(failed.get()), now);
            return true;
        });
    }

    @Override
    public boolean postpone(Instant now, Duration by) {
        return guarded("postpone", () -> {
            Optional<Season> ending = idleActiveSeason();
            if (ending.isEmpty() || by.isNegative() || by.isZero()) {
                return false;
            }
            Instant newEnd = ending.get().end().plus(by);
            reschedule(ending.get(), newEnd);
            announcer.postponed(ending.get().id(), newEnd);
            return true;
        });
    }

    /** Runs one guarded admin or scheduler step. A call while another step runs is refused, as is a database error. */
    private boolean guarded(String operation, BooleanSupplier step) {
        if (busy) {
            LOGGER.fine("season " + operation + " refused, another season step is running");
            return false;
        }
        busy = true;
        try {
            return step.getAsBoolean();
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "season " + operation + " refused: " + failure.getMessage(), failure);
            return false;
        } finally {
            busy = false;
        }
    }

    private void execute(SeasonAction action, Instant now) {
        switch (action) {
            case SeasonAction.Announce notice -> {
                announcer.upcoming(notice.season(), notice.kind().offset(), notice.boundary());
                runs.save(runs.find(notice.season()).orElseGet(() -> planned(notice.season()))
                        .withAnnounced(notice.key()));
            }
            case SeasonAction.WarnNoNext warning -> {
                announcer.adminAlert("no season follows " + activeId.value()
                        + ", so it keeps running; configure the next season");
                lastWarning = Optional.of(warning.at());
            }
            case SeasonAction.Prepare prepare -> transition().perform(prepare, now);
            case SeasonAction.Switch clock -> transition().perform(clock, now);
            case SeasonAction.Resume resume -> transition().perform(resume, now);
            case SeasonAction.None none -> {
            }
        }
    }

    private SeasonTransition transition() {
        return new SeasonTransition(catalog, runs, worlds, challenges, announcer, resets, this::notifyListeners,
                seeds, id -> activeId = id);
    }

    /** Every listener runs even when an earlier one fails. The switch is already committed and is not rolled back. */
    private void notifyListeners(SeasonId from, SeasonId to) {
        for (SeasonTransitionListener listener : listeners) {
            try {
                listener.onSwitched(from, to);
            } catch (RuntimeException failure) {
                LOGGER.log(Level.SEVERE, "season transition listener " + listener.getClass().getName()
                        + " failed for " + from.value() + " -> " + to.value(), failure);
                announcer.adminAlert("a transition listener failed for season " + from.value()
                        + " -> " + to.value() + ": " + failure.getMessage());
            }
        }
    }

    /**
     * Reads the active run and applies what it persists: the season it names becomes the cached {@link #id()}, and a
     * stored effective end replaces the configured end. Without any configured season the database is not read.
     */
    private Optional<SeasonRun> reconcile() {
        if (catalog.seasons().isEmpty()) {
            return Optional.empty();
        }
        Optional<SeasonRun> active = runs.active();
        activeId = active.map(SeasonRun::seasonId).orElse(SeasonId.LEGACY);
        active.filter(run -> run.endsAt() != null).ifPresent(run -> season(run.seasonId())
                .filter(season -> !season.end().equals(run.endsAt()))
                .ifPresent(season -> shift(season, run.endsAt())));
        return active;
    }

    private List<SeasonRun> knownRuns(Optional<SeasonRun> active) {
        List<SeasonRun> known = new ArrayList<>(runs.inFlight());
        active.ifPresent(known::add);
        return known;
    }

    /**
     * The notices already sent. Each one is recorded on the run of the season it announces, so the union over the
     * known runs holds every sent notice, also while no season is active yet.
     */
    private static Set<SeasonAction.Announce> sentNotices(List<SeasonRun> known) {
        return known.stream()
                .flatMap(run -> run.announced().stream())
                .map(SeasonAction.Announce::fromKey)
                .collect(Collectors.toSet());
    }

    /** The run of a season that has not been prepared yet, holding only the notices sent for it. */
    private SeasonRun planned(SeasonId id) {
        return new SeasonRun(id, SeasonTransition.worldKey(id), 0L, SeasonPhase.PLANNED, 0, null, null);
    }

    /** The active season, unless a transition step is running right now. */
    private Optional<Season> idleActiveSeason() {
        Optional<SeasonRun> active = reconcile();
        if (runs.inFlight().stream().anyMatch(run -> RUNNING.contains(run.phase()))) {
            return Optional.empty();
        }
        return active.flatMap(run -> season(run.seasonId()));
    }

    private Optional<Season> season(SeasonId id) {
        return catalog.seasons().stream().filter(season -> season.id().equals(id)).findFirst();
    }

    /** Moves the end of {@code ending} to {@code newEnd}, persists it on the run, and shifts overlapping seasons. */
    private void reschedule(Season ending, Instant newEnd) {
        shift(ending, newEnd);
        runs.find(ending.id()).ifPresent(run -> runs.save(run.withEndsAt(newEnd)));
    }

    /** Moves the end of {@code ending} to {@code newEnd} in the catalog and shifts every later season that overlaps. */
    private void shift(Season ending, Instant newEnd) {
        List<Season> rescheduled = new ArrayList<>();
        Instant previousEnd = null;
        for (Season season : catalog.seasons()) {
            Season current = season;
            if (season.id().equals(ending.id())) {
                current = window(season, season.start(), Duration.between(season.start(), newEnd));
            } else if (previousEnd != null && season.start().isBefore(previousEnd)) {
                current = window(season, previousEnd, season.duration());
            }
            rescheduled.add(current);
            previousEnd = current.end();
        }
        catalog = new SeasonCatalog(rescheduled);
    }

    private static Season window(Season season, Instant start, Duration duration) {
        return new Season(season.id(), season.displayName(), start, duration, season.preset(),
                season.challenges(), season.seed());
    }

}

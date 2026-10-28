package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonPhase;
import net.onelitefeather.skygridng.api.model.SeasonRun;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.out.PlayerSeasonReset;
import net.onelitefeather.skygridng.ports.out.SeasonAnnouncer;
import net.onelitefeather.skygridng.ports.out.SeasonRunRepository;
import net.onelitefeather.skygridng.ports.out.SeasonTransitionListener;
import net.onelitefeather.skygridng.ports.out.SeasonWorlds;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static net.onelitefeather.skygridng.api.model.SeasonPhase.ACTIVE;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.ARCHIVED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.ARCHIVE_FAILED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.ARCHIVING;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.CLOSING;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PLANNED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PREPARED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PREPARE_FAILED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PREPARING;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.UNLOADING;

/**
 * The steps of a season transition. Every step first persists its target phase, then performs its side effect,
 * then persists the phase it reached, so a restart resumes from the last persisted phase. The dispatch in
 * {@link #resume} is one exhaustive switch over {@link net.onelitefeather.skygridng.api.model.SeasonPhase} with no
 * default branch: a new phase fails to compile until it has a step.
 *
 * <p>A new instance is built per scheduler tick from the live catalog; it holds no state of its own.
 */
public final class SeasonTransition {

    private static final String WORLD_KEY_PREFIX = "skygridng:season_";

    private final SeasonCatalog catalog;
    private final SeasonRunRepository runs;
    private final SeasonWorlds worlds;
    private final ChallengeAdminUseCase challenges;
    private final SeasonAnnouncer announcer;
    private final PlayerSeasonReset resets;
    private final SeasonTransitionListener listeners;
    private final LongSupplier seeds;
    private final Consumer<SeasonId> activeChanged;

    public SeasonTransition(
            SeasonCatalog catalog,
            SeasonRunRepository runs,
            SeasonWorlds worlds,
            ChallengeAdminUseCase challenges,
            SeasonAnnouncer announcer,
            PlayerSeasonReset resets,
            SeasonTransitionListener listeners,
            LongSupplier seeds,
            Consumer<SeasonId> activeChanged) {
        this.catalog = catalog;
        this.runs = runs;
        this.worlds = worlds;
        this.challenges = challenges;
        this.announcer = announcer;
        this.resets = resets;
        this.listeners = listeners;
        this.seeds = seeds;
        this.activeChanged = activeChanged;
    }

    /** Performs the transition part of {@code action}. Notices, warnings and {@code None} are handled by the service. */
    public void perform(SeasonAction action, Instant now) {
        switch (action) {
            case SeasonAction.Prepare prepare -> prepare(prepare.season());
            case SeasonAction.Switch clock -> switchTo(clock.from(), clock.to(), now);
            case SeasonAction.Resume resume -> resume(resume.run(), now);
            case SeasonAction.Announce announce -> {
            }
            case SeasonAction.WarnNoNext warning -> {
            }
            case SeasonAction.None none -> {
            }
        }
    }

    /** Resumes the transition at the step of the run's persisted phase. */
    private void resume(SeasonRun run, Instant now) {
        Runnable step = switch (run.phase()) {
            case PLANNED, ACTIVE, ARCHIVED -> () -> {
            };
            case PREPARING -> () -> stepPreparing(run);
            case PREPARED -> () -> stepPrepared(run, now);
            case CLOSING -> () -> stepClosing(run);
            case UNLOADING -> () -> stepUnloading(run);
            case ARCHIVING -> () -> stepArchiving(run);
            case PREPARE_FAILED -> () -> retryPreparation(run);
            case ARCHIVE_FAILED -> () -> retryArchive(run);
        };
        step.run();
    }

    /** Persists the new season's run with its seed, then creates its world. */
    private void prepare(Season season) {
        long seed = season.seed() != null ? season.seed() : seeds.getAsLong();
        SeasonRun preparing = new SeasonRun(season.id(), WORLD_KEY_PREFIX + season.id().value(), seed,
                PREPARING, 0, null, null);
        runs.save(preparing);
        stepPreparing(preparing);
    }

    private void stepPreparing(SeasonRun run) {
        if (createOrLoad(run)) {
            runs.save(withPhase(run, PREPARED, run.attempts()));
        }
    }

    /**
     * A prepared world is loaded again, idempotently. After the boundary the switch follows: the ending season is
     * the one active in the database, and the boundary is its end.
     */
    private void stepPrepared(SeasonRun run, Instant now) {
        if (!createOrLoad(run)) {
            return;
        }
        Optional<SeasonRun> ending = runs.active();
        if (ending.isEmpty()) {
            return;
        }
        Optional<Season> endingSeason = season(ending.get().seasonId());
        if (endingSeason.isEmpty() || now.isBefore(endingSeason.get().end())) {
            return;
        }
        switchTo(ending.get().seasonId(), run.seasonId(), now);
    }

    /**
     * Commits the switch. The ending season's challenges are deactivated first, while the database still names it
     * as the active season, so the round boundary is credited to it. Then the incoming season's challenge set is
     * applied, the listeners run and the season start is announced.
     */
    private void switchTo(SeasonId from, SeasonId to, Instant now) {
        Season ending = season(from).orElseThrow(() -> new IllegalStateException("unknown season " + from.value()));
        Season incoming = season(to).orElseThrow(() -> new IllegalStateException("unknown season " + to.value()));
        for (String challenge : ending.challenges()) {
            challenges.deactivate(challenge);
        }
        runs.switchActive(from, to, now);
        // The incoming season keeps its effective end across a restart, even when an earlier postponement shifted it.
        runs.find(to).ifPresent(run -> runs.save(run.withEndsAt(incoming.end())));
        activeChanged.accept(to);
        activateExactly(incoming);
        listeners.onSwitched(from, to);
        announcer.started(to);
    }

    /** Closing step: applies the incoming set again (idempotent), moves the players out and marks the unload. */
    private void stepClosing(SeasonRun ending) {
        Optional<SeasonRun> incoming = runs.active();
        if (incoming.isEmpty()) {
            return;
        }
        season(incoming.get().seasonId()).ifPresent(this::activateExactly);
        if (worlds.isLoaded(ending.worldKey())) {
            moveEverybody(ending, incoming.get());
        }
        runs.save(withPhase(ending, UNLOADING, ending.attempts()));
    }

    /** Unload step: moves whoever is still inside, then unloads. A refused unload is retried on a later tick. */
    private void stepUnloading(SeasonRun ending) {
        if (worlds.isLoaded(ending.worldKey())) {
            Optional<SeasonRun> incoming = runs.active();
            if (incoming.isEmpty()) {
                return;
            }
            moveEverybody(ending, incoming.get());
            if (!worlds.unload(ending.worldKey())) {
                return;
            }
        }
        runs.save(withPhase(ending, ARCHIVING, ending.attempts()));
    }

    /**
     * Archive step. An archived copy with the folder still present is a conflict; a folder with no archived copy is
     * archived; an archived copy without a folder means the move already happened.
     */
    private void stepArchiving(SeasonRun ending) {
        boolean archived = worlds.archived(ending.worldKey());
        boolean folder = worlds.exists(ending.worldKey());
        if (archived && !folder) {
            runs.save(withPhase(ending, ARCHIVED, ending.attempts()));
            return;
        }
        if (archived || !folder) {
            runs.save(withPhase(ending, ARCHIVE_FAILED, ending.attempts() + 1));
            announcer.adminAlert("archive of " + ending.worldKey() + " needs a manual check");
            return;
        }
        try {
            worlds.archive(ending.worldKey(), ending);
        } catch (RuntimeException failure) {
            runs.save(withPhase(ending, ARCHIVE_FAILED, ending.attempts() + 1));
            announcer.adminAlert("could not archive " + ending.worldKey() + ": " + failure.getMessage());
            return;
        }
        runs.save(withPhase(ending, ARCHIVED, ending.attempts()));
    }

    private void retryPreparation(SeasonRun failed) {
        SeasonRun preparing = withPhase(failed, PREPARING, failed.attempts());
        runs.save(preparing);
        stepPreparing(preparing);
    }

    private void retryArchive(SeasonRun failed) {
        SeasonRun archiving = withPhase(failed, ARCHIVING, failed.attempts());
        runs.save(archiving);
        stepArchiving(archiving);
    }

    /**
     * Creates the world, or loads it when its folder exists, with the stored seed. A failure is persisted as
     * PREPARE_FAILED with one more attempt and reported; the active season is not touched.
     */
    private boolean createOrLoad(SeasonRun run) {
        try {
            String preset = season(run.seasonId())
                    .orElseThrow(() -> new IllegalStateException("season " + run.seasonId().value() + " is not configured"))
                    .preset();
            worlds.prepare(run.worldKey(), run.seed(), preset);
            return true;
        } catch (RuntimeException failure) {
            runs.save(withPhase(run, PREPARE_FAILED, run.attempts() + 1));
            announcer.adminAlert("could not create the world of season " + run.seasonId().value()
                    + ": " + failure.getMessage());
            return false;
        }
    }

    /** Moves every player out of the ending world and resets each one for the incoming season. */
    private void moveEverybody(SeasonRun ending, SeasonRun incoming) {
        List<UUID> moved = worlds.evacuate(ending.worldKey(), incoming.worldKey());
        for (UUID player : moved) {
            resets.reset(player, incoming.seasonId());
        }
    }

    /** Makes the challenge set exactly {@code incoming}: every other active challenge is deactivated. */
    private void activateExactly(Season incoming) {
        for (ChallengeRule rule : challenges.list()) {
            if (!incoming.challenges().contains(rule.id()) && challenges.isActive(rule.id())) {
                challenges.deactivate(rule.id());
            }
        }
        for (String challenge : incoming.challenges()) {
            if (!challenges.isActive(challenge)) {
                challenges.activate(challenge);
            }
        }
    }

    private Optional<Season> season(SeasonId id) {
        return catalog.seasons().stream().filter(season -> season.id().equals(id)).findFirst();
    }

    private static SeasonRun withPhase(SeasonRun run, SeasonPhase phase, int attempts) {
        return new SeasonRun(run.seasonId(), run.worldKey(), run.seed(), phase, attempts,
                run.startedAt(), run.endedAt(), run.endsAt(), run.announced());
    }
}

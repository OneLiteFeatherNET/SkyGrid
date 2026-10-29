package net.onelitefeather.skygridng.season;

import net.onelitefeather.skygridng.challenge.ChallengeRule;
import net.onelitefeather.skygridng.challenge.ChallengeAdminUseCase;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * In memory doubles of the season out ports, shared by the transition and service tests. Every double writes
 * to one {@link Journal} so a test can assert the order of the side effects. Nothing is static or shared: each
 * test builds its own instances.
 */
final class SeasonFakes {

    static final Instant JAN = Instant.parse("2027-01-01T00:00:00Z");
    static final Duration FOUR_WEEKS = Duration.ofDays(28);

    private SeasonFakes() {
    }

    static Season season(String id, Instant start, String preset, String... challenges) {
        return new Season(new SeasonId(id), id, start, FOUR_WEEKS, preset, List.of(challenges), null);
    }

    static SeasonRun run(String id, SeasonPhase phase) {
        return new SeasonRun(new SeasonId(id), "skygridng:season_" + id, 7L, phase, 0, null, null);
    }

    /** The ordered record of every side effect a double performed. */
    static final class Journal {
        private final List<String> entries = new ArrayList<>();

        void record(String entry) {
            entries.add(entry);
        }

        List<String> entries() {
            return List.copyOf(entries);
        }

        long count(String prefix) {
            return entries.stream().filter(entry -> entry.startsWith(prefix)).count();
        }
    }

    /** Season runs keyed by season id. A down database makes every call throw, as an unreachable Postgres does. */
    static final class Runs implements SeasonRunRepository {
        private final Journal journal;
        private final Map<SeasonId, SeasonRun> rows = new LinkedHashMap<>();
        boolean down;

        Runs(Journal journal, SeasonRun... initial) {
            this.journal = journal;
            for (SeasonRun run : initial) {
                rows.put(run.seasonId(), run);
            }
        }

        private void requireUp() {
            if (down) {
                throw new IllegalStateException("database is unreachable");
            }
        }

        SeasonRun get(String id) {
            return rows.get(new SeasonId(id));
        }

        @Override
        public Optional<SeasonRun> find(SeasonId seasonId) {
            requireUp();
            return Optional.ofNullable(rows.get(seasonId));
        }

        @Override
        public Optional<SeasonRun> active() {
            requireUp();
            return rows.values().stream().filter(run -> run.phase() == SeasonPhase.ACTIVE).findFirst();
        }

        @Override
        public List<SeasonRun> inFlight() {
            requireUp();
            return rows.values().stream()
                    .filter(run -> run.phase() != SeasonPhase.ACTIVE && run.phase() != SeasonPhase.ARCHIVED)
                    .toList();
        }

        @Override
        public void save(SeasonRun run) {
            requireUp();
            journal.record("save:" + run.seasonId().value() + ":" + run.phase());
            rows.put(run.seasonId(), run);
        }

        @Override
        public void switchActive(SeasonId from, SeasonId to, Instant at) {
            requireUp();
            journal.record("switch:" + from.value() + "->" + to.value());
            SeasonRun ending = rows.get(from);
            SeasonRun incoming = rows.get(to);
            if (ending != null) {
                rows.put(from, new SeasonRun(from, ending.worldKey(), ending.seed(), SeasonPhase.CLOSING,
                        ending.attempts(), ending.startedAt(), at, ending.endsAt(), ending.announced()));
            }
            rows.put(to, new SeasonRun(to, incoming.worldKey(), incoming.seed(), SeasonPhase.ACTIVE,
                    incoming.attempts(), at, null, incoming.endsAt(), incoming.announced()));
        }
    }

    /** Season worlds that can be told to fail a creation, to refuse an unload a number of times, or to hold players. */
    static final class Worlds implements SeasonWorlds {
        private final Journal journal;
        final Set<String> loaded = new HashSet<>();
        final Set<String> folders = new HashSet<>();
        final Set<String> archived = new HashSet<>();
        final Set<String> failPrepare = new HashSet<>();
        final List<UUID> playersInWorld = new ArrayList<>();
        int unloadRefusals;
        /** Runs in the middle of {@link #prepare}, so a test can call back into the service. */
        Runnable beforePrepare = () -> {
        };

        Worlds(Journal journal) {
            this.journal = journal;
        }

        @Override
        public void prepare(String worldKey, long seed, String preset) {
            journal.record("prepare:" + worldKey + ":" + seed + ":" + preset);
            beforePrepare.run();
            if (failPrepare.contains(worldKey)) {
                throw new IllegalStateException("cannot create world " + worldKey);
            }
            folders.add(worldKey);
            loaded.add(worldKey);
        }

        @Override
        public boolean isLoaded(String worldKey) {
            return loaded.contains(worldKey);
        }

        @Override
        public boolean exists(String worldKey) {
            return folders.contains(worldKey);
        }

        @Override
        public boolean archived(String worldKey) {
            return archived.contains(worldKey);
        }

        @Override
        public List<UUID> evacuate(String fromKey, String toKey) {
            journal.record("evacuate:" + fromKey + "->" + toKey);
            List<UUID> moved = List.copyOf(playersInWorld);
            playersInWorld.clear();
            return moved;
        }

        @Override
        public boolean unload(String worldKey) {
            journal.record("unload:" + worldKey);
            if (unloadRefusals > 0) {
                unloadRefusals--;
                return false;
            }
            loaded.remove(worldKey);
            return true;
        }

        @Override
        public void archive(String worldKey, SeasonRun manifest) {
            journal.record("archive:" + worldKey);
            if (archived.contains(worldKey)) {
                throw new IllegalStateException("archive target already exists for " + worldKey);
            }
            folders.remove(worldKey);
            archived.add(worldKey);
        }
    }

    /** Challenges with an active flag per id, as the admin port exposes them. */
    static final class Challenges implements ChallengeAdminUseCase {
        private final Journal journal;
        private final Map<String, Boolean> active = new LinkedHashMap<>();

        Challenges(Journal journal, String... activeIds) {
            this.journal = journal;
            for (String id : activeIds) {
                active.put(id, true);
            }
        }

        void known(String id) {
            active.putIfAbsent(id, false);
        }

        @Override
        public List<ChallengeRule> list() {
            return active.entrySet().stream()
                    .map(entry -> new ChallengeRule(entry.getKey(), "", entry.getValue()))
                    .toList();
        }

        @Override
        public boolean isActive(String id) {
            return active.getOrDefault(id, false);
        }

        @Override
        public boolean activate(String id) {
            if (!active.containsKey(id)) {
                return false;
            }
            journal.record("activate:" + id);
            active.put(id, true);
            return true;
        }

        @Override
        public boolean deactivate(String id) {
            if (!active.containsKey(id)) {
                return false;
            }
            journal.record("deactivate:" + id);
            active.put(id, false);
            return true;
        }

        @Override
        public void addChangeListener(Runnable listener) {
        }
    }

    static final class Announcer implements SeasonAnnouncer {
        private final Journal journal;
        final List<String> alerts = new ArrayList<>();

        Announcer(Journal journal) {
            this.journal = journal;
        }

        @Override
        public void upcoming(SeasonId season, Duration remaining, Instant boundary) {
            journal.record("upcoming:" + season.value() + ":" + remaining);
        }

        @Override
        public void started(SeasonId season) {
            journal.record("started:" + season.value());
        }

        @Override
        public void postponed(SeasonId season, Instant newEnd) {
            journal.record("postponed:" + season.value() + ":" + newEnd);
        }

        @Override
        public void adminAlert(String message) {
            alerts.add(message);
            journal.record("alert");
        }
    }

    /** Collects the records of one logger, so a test asserts on the log record, not on console output. */
    static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> records() {
            return List.copyOf(records);
        }
    }

    static final class Resets implements PlayerSeasonReset {
        private final Journal journal;

        Resets(Journal journal) {
            this.journal = journal;
        }

        @Override
        public void reset(UUID playerId, SeasonId season) {
            journal.record("reset:" + playerId + ":" + season.value());
        }
    }
}

# Design

## Context

See proposal.md, Why and What Changes, for motivation and scope. The requirements are in
specs/season-lifecycle/spec.md; this document explains how they are built.

Current state: one SkyGrid world, challenges toggled through `ChallengeAdminUseCase`, tier and task
progress stored without a season column, `ChallengeProgressRepository#resetAll` as the stand-in
reset (I2a). The plugin loads at server `STARTUP`, so world generators exist before worlds load.
Persistence is PostgreSQL through HikariCP, Hibernate and Flyway. The Flyway per-adapter fix (PR #9)
gives each adapter its own migration location and history table, which this design relies on.

Constraints that shape the approach: `api`, `ports`, `core` and the framework-free adapters must not
import Bukkit, Guice or jakarta.inject; the season Bukkit side lives in `platforms.olf.season`
(OLF-specific, mirroring challenges); time comes only from an injected `java.time.Clock`; seeds come
only from an injected `LongSupplier`.

## Goals / Non-Goals

**Goals:**

- A transition that is idempotent per step, persists its phase before and after each side effect,
  and resumes after any restart.
- Exactly one `ACTIVE` season at any time, enforced by the database.
- Pure, fast unit tests for all scheduling logic (`SeasonTimeline`) with plain `Instant`s.
- A shared `ProgressScope` key that the prestige and economy changes can consume without another
  migration of the task completion table.

**Non-Goals:**

- Prestige steps above 0 (prestige change), currency balance reset and ledger (economy change).
- The external BlueMap render of archived worlds. This change only provides the archive directory
  hand-off point.
- A sidebar or boss-bar countdown (I3e AC4, Won't for this release).
- Per-player timers, purchasable carry-over, "last chance" offers.
- Multi-server support: one SkyGrid service per database; no realm column (decision 6).

## Decisions

### D1. Placement in the hexagon

| Layer | Content |
|---|---|
| `api.model` | `SeasonId`, `Season`, `SeasonCatalog`, `SeasonPhase`, `SeasonRun`, `SeasonStatus`, `ProgressScope` |
| `api` | `SkyGridApi#currentSeason()` (read only, `Optional<Season>`) |
| `ports.in` | `SeasonAdminUseCase`, `CurrentSeason` |
| `ports.out` | `SeasonRepository` (config, read only), `SeasonRunRepository`, `SeasonWorlds`, `PlayerSeasonReset`, `SeasonAnnouncer`; changed `ChallengeProgressRepository`, `TaskCompletionRepository` |
| `core.service` | `SeasonTimeline`, `SeasonService`, `SeasonTransition`; changed `ChallengeProgressService`, `TaskProgressService` |
| `adapters/config` | `SeasonDtos`, `JacksonSeasonRepository`, `ConfigSchemas.Kind.SEASON` |
| `adapters/season` (new) | `PostgresSeasonRunRepository`, `SeasonRunEntity` |
| `adapters/challenge-progress`, `adapters/task-progress` | `season_id` (and prestige step) migrations |
| `adapters/command` | `SeasonCommands`, `Messages.season*` |
| `platforms/common` | `platforms.olf.season`: `BukkitSeasonWorlds`, `BukkitPlayerSeasonReset`, `BukkitSeasonAnnouncer`, `SeasonTicker`, `SeasonJoinListener`, `SeasonFeature`, `SeasonModule` |

Rationale: the season is OLF-specific, so its Bukkit code follows the challenge convention in
`platforms.olf`. The core keeps the scheduling and state logic and talks to the platform only
through ports, so the ArchUnit rules in `verification` apply without exceptions.

### D2. Domain model

- `SeasonId(String)`: pattern `[a-z0-9_]{1,64}`, same bound as `ChallengeRule.MAX_ID_LENGTH`.
  `SeasonId.LEGACY = new SeasonId("legacy")`.
- `Season`: id, display name, start, positive duration, preset, challenge set, optional seed.
  `end() = start + duration`; `contains(Instant)` is start inclusive, end exclusive.
- `SeasonCatalog`: seasons ordered by start, with `next(SeasonId)` and `at(Instant)`.
- `SeasonPhase`: `PLANNED, PREPARING, PREPARED, ACTIVE, CLOSING, UNLOADING, ARCHIVING, ARCHIVED,
  PREPARE_FAILED, ARCHIVE_FAILED`.
- `SeasonRun`: season id, world key, seed, phase, started and ended instants, attempts. This is the
  persisted runtime fact; season definitions stay in config (single source of truth).
- `ProgressScope(SeasonId season, UUID playerId, String challengeId, int prestigeStep)`: prestige
  step between 0 and 10 (the prestige ladder maximum), challenge id under the `ChallengeRule` rules.
  Task completion is keyed by the scope plus `taskId`. The season tier counter is keyed by the scope
  without the step, so a prestige step does not split a tier.

All records validate in compact constructors and are immutable.

### D3. Ports

- `ports.in.CurrentSeason`: `SeasonId id()` and `Optional<Season> season()`. Returns
  `SeasonId.LEGACY` when no season is configured. Progress services resolve it per call, so the four
  `ChallengeBehavior` implementations do not change.
- `ports.in.SeasonAdminUseCase`: `status()`, `list()`, `tick(Instant)` (idempotent scheduler entry),
  `advanceNow()`, `retry()`, `postpone(Duration)`, `reload()`, `history(UUID)`, and a transition
  listener registration.
- `ports.out.SeasonRepository`: `load()` only (config is never written by the plugin).
- `ports.out.SeasonRunRepository`: `find(SeasonId)`, `active()`, `inFlight()` (phases other than
  `ACTIVE` and `ARCHIVED`), `save(SeasonRun)`, `switchActive(from, to, at)` as one transaction.
- `ports.out.SeasonWorlds`: `prepare(worldKey, seed, preset)`, `isLoaded`, `exists`,
  `evacuate(from, to)`, `unload(worldKey)`, `archive(worldKey, manifest)`. Main-thread contract is
  documented on the port.
- `ports.out.PlayerSeasonReset`, `ports.out.SeasonAnnouncer` (`upcoming`, `started`, `adminAlert`).
  Message keys stay out of the core.
- Time: the core receives a `Clock`, bound to `Clock.systemUTC()` in the platform. Seed: the core
  receives a `LongSupplier`, bound to `SecureRandom::nextLong` in the platform and fixed in tests.

Changed progress ports (season-scoped, breaking inside the repository, since `ports` is not
published): `tierOf(SeasonId, UUID, String)`, `recordCompletion(SeasonId, UUID, String)`,
`resetSeason(SeasonId)` (replaces `resetAll`), `history(UUID)`, and for tasks
`resetSeason(SeasonId)` and `completedCountBySeason(UUID)`.

### D4. Core services

- `SeasonTimeline` (pure): validates the catalog (overlap, unknown preset and challenge given the
  known id sets) and answers `nextAction(now, catalog, runs)` with one of `None`, `Announce(kind, at)`,
  `Prepare(season, at)`, `Switch(from, to, at)`, `Resume(run)`, `WarnNoNext`. All due-time math lives
  here.
- `SeasonTransition`: one step per phase, persisting the phase before and after every side effect
  (see D6). Each step first checks observable state (world loaded, folder present, run phase) and
  skips work already done.
- `SeasonService`: implements `SeasonAdminUseCase` and `CurrentSeason`; owns the live catalog, a
  cached active season id refreshed on switch, the listeners, and a single-flight guard so `tick`,
  `advanceNow` and `retry` never run two transitions at once.

### D5. Persistence: Postgres `adapters/season`

`PostgresSeasonRunRepository` extends the shared JPA base with persistence unit `season`, location
`classpath:db/migration/season` and history table `flyway_history_season` (the per-adapter pattern
from the Flyway fix). Same lazy pool, `DatabaseConfig::fromEnvironment`, `hbm2ddl=validate`.

```sql
-- db/migration/season/V1__create_season_run.sql
CREATE TABLE season_run (
    season_id  TEXT PRIMARY KEY CHECK (char_length(season_id) <= 64),
    world_key  TEXT NOT NULL CHECK (char_length(world_key) <= 128),
    seed       BIGINT NOT NULL,
    phase      TEXT NOT NULL CHECK (phase IN ('PLANNED','PREPARING','PREPARED','ACTIVE','CLOSING',
               'UNLOADING','ARCHIVING','ARCHIVED','PREPARE_FAILED','ARCHIVE_FAILED')),
    attempts   INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    started_at TIMESTAMPTZ,
    ended_at   TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX season_run_single_active ON season_run ((phase)) WHERE phase = 'ACTIVE';
```

The partial unique index means a second `ACTIVE` row cannot be written, so there is never a moment
with two active seasons.

Progress migrations, applied in the existing adapters (archive by key, no copy):

- `db/migration/challenge-progress/V3__scope_tier_by_season.sql`: add `season_id TEXT NOT NULL
  DEFAULT 'legacy'` (64 check), drop the default, replace the primary key with
  `(season_id, player_id, challenge_id)`.
- `db/migration/task-progress/V2__scope_completion_by_season_and_step.sql`: the only migration of the
  task completion table in the whole plan. Add `season_id` (default `legacy`, 64 check) and
  `prestige_step SMALLINT NOT NULL DEFAULT 0 CHECK (prestige_step BETWEEN 0 AND 10)`, drop both
  defaults, replace the primary key with `(season_id, player_id, challenge_id, prestige_step, task_id)`.
  Every existing row becomes season `legacy`, step 0, so no player loses a completion.

### D6. Transition state machine

Happy path, current season A, next season B:

1. **Announce** (T-24h, T-1h, T-5m): `SeasonAnnouncer#upcoming`. No state change.
2. **Prepare** (T minus `prepareLead`): resolve the seed; persist `season_run(B, key, seed, PREPARING)`
   before any world call; on the main thread `SeasonWorlds#prepare`; on success persist `PREPARED`.
   Players cannot reach B yet.
3. **Close A** (T): deactivate A's challenges through `ChallengeAdminUseCase`. This fires the existing
   round-boundary hooks (one_life survivor tiers, task completions) while `CurrentSeason` still returns
   A, so the final round is credited to A.
4. **Switch** (one transaction, `switchActive(A, B, now)`): A becomes `CLOSING` with `ended_at`, B
   becomes `ACTIVE` with `started_at`. Refresh the cached `CurrentSeason`. From here every write goes
   to B.
5. **Activate B's challenges**: activate exactly B's set, deactivate the rest, fire the transition
   listeners, `SeasonAnnouncer#started`.
6. **Move players** (main thread): `evacuate(A, B)` teleports each player in A to a spread slot in B.
   `PlayerSeasonReset` runs on arrival; the PDC marker `skygridng:season = <id>` prevents a second
   reset.
7. **Unload A**: persist `UNLOADING`; `unload(A)` with save, only when `Bukkit.isTickingWorlds()` is
   false.
8. **Archive A** (async after a successful unload): persist `ARCHIVING`; move the folder, write
   `season.json`; persist `ARCHIVED`.

Restart table, evaluated by `tick` on `ServerLoadEvent` using `inFlight()`:

| Persisted state | Resume action |
|---|---|
| B `PREPARING` | if `exists(B)` load it with the stored seed, else create it with the stored seed; then `PREPARED` |
| B `PREPARED`, before T | load B (no players); wait for T |
| B `PREPARED`, after T | continue at step 3 (challenge deactivation is idempotent) |
| B `ACTIVE`, A `CLOSING` | load B; re-apply B's challenge set; go to step 6, then 7 |
| A `UNLOADING` | if A is still loaded, evacuate and unload; else go to step 8 |
| A `ARCHIVING` | if the target exists and the source does not, mark `ARCHIVED`; if both exist, mark `ARCHIVE_FAILED` |

Failure handling:

- **World creation fails** (null, exception, disk full): B becomes `PREPARE_FAILED` with
  `attempts + 1`; A stays `ACTIVE`, untouched (step 3 never ran). Automatic retry after 5 minutes, at
  most 3 attempts, then only `/skygrid season retry`. Admins get an alert on join and in `status`. If
  T passes while B is not `PREPARED`, A simply continues (gap behaviour) and players see "the next
  season is delayed" once.
- **Database down**: no step runs, because every step persists first. The world keeps running and
  `status` shows the reason.
- **Player joins during steps 6 and 7**: `SeasonJoinListener` routes them into B, so A empties.
  `unload` returning false is retried every tick (at most 200 ticks) after a fresh `evacuate`.
- **Teleport fails** (chunk load error): fall back to the high point in B, never leave a player in A.
- **Archive move fails**: `ARCHIVE_FAILED`, files untouched, B fully playable, admin alert.
- **Server stops during world creation**: the half-written folder is reused on resume, because the
  seed is identical and Paper loads an existing folder rather than regenerating it.

### D7. Platform wiring (`platforms.olf.season`)

- World creation: `WorldCreator.ofKey(NamespacedKey.fromString(worldKey))`, `.seed(seed)`,
  `.generator(worldGenerators.forWorld(key, preset))`, `.environment(NORMAL)`, plus
  `forcedSpawnPosition(...)` (new in 26.1) so Paper skips its spawn search in the void; then
  `Bukkit.createWorld(creator)`. A null result or an exception becomes `PrepareResult.Failed`.
  Dimensions live under `world/dimensions/<ns>/<key>/` since 26.1.
- Thread rules: create, unload and teleport run on the main thread and only when
  `Bukkit.isTickingWorlds()` is false. The archive file move and manifest write run async.
- Main world: the server `level-name` world stays a small static void lobby (decision 3). It cannot
  be unloaded, so it is never a season world. Players join directly into the active season world.
- Startup: `SeasonFeature` does not create worlds in `onEnable`. It listens for `ServerLoadEvent`
  (`STARTUP`) and calls `tick`, which loads the active season world (a load when the folder exists)
  and resumes in-flight runs.
- Scheduling: `SeasonTicker` runs `runTaskTimer` every 600 ticks (30 seconds) and calls
  `tick(clock.instant())`. The ticker holds no logic. Database reads in `tick` run on an async task,
  and the resulting world step is handed back to the main thread.
- Joins: `SeasonJoinListener` on `PlayerSpawnLocationEvent` (the event `SpreadSpawnListener` already
  uses) routes a player whose spawn world is not the active season world into it, and applies the
  one-time reset.
- Challenge scoping: the existing `Predicate<World>` (generator is `HexagonalChunkGenerator`) narrows
  to "is the active season world" when seasons are configured, so a prepared but not yet active world
  never triggers challenge logic.
- Commands (`SeasonCommands`, Cloud annotations, `CommandSource`, no Bukkit): `season info` (permission
  `skygridng.season`, default true), `season history [player]` (other players need
  `skygridng.admin`), `season list|status|retry`, `season advance [confirm]`,
  `season postpone <duration> confirm`, `season reload [confirm]` (admin). Suggestion provider
  `season-ids` is registered in `CloudCommandRegistrar`.
- Messages: `Messages.season*` factory methods with keys such as `season.info`,
  `season.history.entry`, `season.status`, `season.announce.upcoming`, `season.announce.started`,
  `season.advance.confirm`, `season.transition.failed`, `season.no_next` in `Bundle_en.properties`
  and `Bundle_de.properties`. Dates use the viewer locale.

### D8. Configuration

```yaml
# seasons/s01_overgrowth.yml
id: s01_overgrowth
displayName: "<green>Season 1: Overgrowth"   # MiniMessage, admin authored like task rewards
start: 2026-12-01T18:00:00Z                  # ISO-8601 instant, UTC recommended
duration: P8W                                # ISO-8601 duration, fixed length
preset: standard                             # must exist in presets/
challenges: [one_life, no_crafting_table]    # must exist in challenges/; may be empty
# seed: 123456789                            # optional; omitted = random at prepare time
```

`config.yml` gains an optional `season` block (`prepareLead`, default `PT15M`; `archive.directory`,
relative to the server root or absolute; `archive.compress`, default false). The bundled default ships
no active season: the example is written as `seasons/example.yml.disabled`, which is not a supported
extension, so it is not loaded. `seasons/` absent: the example is seeded; `seasons/` present but
empty: it stays empty, so an operator can opt out.

### D9. CloudNet static service

A dynamic CloudNet service is rebuilt from its template on every start, which would drop the season
world, the archive and the plugin data folder. The season service must be **static**, or mount a
persistent volume for `world/` and the archive directory. Worlds are never synced back into templates.
The Postgres `season_run` row is authoritative: if the active season's folder is missing at startup,
the plugin regenerates the world from the stored seed (terrain identical, builds lost) and raises a
loud admin alert instead of failing to start. The archive directory is the hand-off point for the
external BlueMap job; a shared read-only mount is the simplest contract.

### D10. Integration points for prestige and economy

- `SeasonTransitionListener#onSwitched(from, to)` fires after the switch commit and before players
  are moved. Prestige can snapshot or reset its state here; economy can decide carry-over.
- `CurrentSeason#id()` is the key both changes use for season-scoped rows.
- `SkyGridApi#currentSeason()` exposes the season to other plugins.
- The vanilla reset clears inventories, so currency must live in Postgres, never in items. Balances are
  season-scoped (decision 2).

### D11. Product decisions (fixed for this release)

1. Vanilla state and progress reset on the first entry to each season (inventory, ender chest, XP,
   health, hunger, effects, respawn point, game mode, tiers, task progress, prestige step). Archived
   rows stay under their season id. The all-time highest prestige step is a prestige-change record.
2. Currency does not carry over. The economy change writes one `SEASON_RESET` entry per account.
3. The server `level-name` world is a static void lobby. The old SkyGrid world becomes season `legacy`
   at the cut-over and is archived once, by hand.
4. No next season at the end instant: the current season is extended and admins get a daily warning.
5. Prestige step is season-scoped like tiers (prestige change).
6. One SkyGrid service per database.
7. `ProgressScope` is owned by this change; the prestige change consumes its `prestigeStep`.

### D12. Requirement priorities (MoSCoW, information only)

| Story | Must | Should | Could | Won't |
|---|---|---|---|---|
| I3a Seasons in config | AC1, AC2, AC3, AC4 | AC5 | | |
| I3b Automatic transition | AC1, AC2, AC3, AC4, AC5, AC6, AC8 | AC7 | | |
| I3c Players move safely | AC1, AC2, AC3, AC4 | | | |
| I3d Per-season archive | AC1, AC2, AC3, AC4, AC5 | | | |
| I3e Visibility | AC1, AC2, AC3 | | | AC4 (no countdown) |
| I3f Admin control | AC1, AC2, AC3, AC5 | AC4 | | |
| I3g Archived worlds | AC1, AC2 | | AC3 | |

## I5 dark-pattern review

Applied item by item from `.claude/skills/challenge-season-system/SKILL.md`, Step 2.

Mechanic in one sentence: fixed-length seasons that reset the world and per-player progress to zero on
a published date, archiving past achievements, to give everyone a fair, fresh start and a predictable
content cadence.

1. **Octalysis drives.** Engages Development and Accomplishment (tiers, tasks), Epic Meaning (season
   themes), Empowerment (fresh world exploration) and light Scarcity (time-boxed season). No
   Unpredictability. Loss and Avoidance appear only as "progress resets". Restated without loss: the
   reset is implemented and presented as an archive ("your Season 1 record is kept"), and history is
   viewable (I3d AC3). Verdict: **pass**.
2. **Full transparency.** Start, end, preset, challenges and reset scope are shown in `season info`
   with absolute dates (I3e AC1). Announcements state exactly what resets. Nothing relies on players
   misjudging timing or cost. Verdict: **pass**.
3. **Bartle types.** Achievers (tasks, tiers), Explorers (new seed and preset each season),
   Socializers (everyone restarts together). Killers are not served, and no PvP mechanic is added, so
   the season does not hurt them; it does not target them. Verdict: **pass**, note for
   `game-designer`.
4. **Reward adaptation.** The mechanic changes no reward rate or magnitude, on any signal. No
   compensation loop. Verdict: **pass**. The prestige change must re-run this item.
5. **Late-join and irregular players.** The Hypixel SkyBlock Bingo precedent applies directly:
   everyone starts from zero at season start, so a late joiner loses time, not relative standing.
   Nothing earned is taken away; it is archived and shown in `history`. Risk: the vanilla reset
   (decision 1) removes items built up in the old world. Verdict: **pass, conditional on decision 1
   being announced** in season info and in the upcoming-transition announcement (I3e AC2).
6. **Skeptical observer.** "The server wipes your stuff every eight weeks" is a plausible one-line
   criticism. Mitigations: wipes are announced in advance, the old world is archived (viewable via
   BlueMap), and history is kept. No "last chance" offers and no purchasable carry-over. Verdict:
   **pass with mitigation**.
7. **Falsifiable metric** (for `playtest-qa-coordinator`): (a) at least 60 percent of players active in
   the last two weeks of season N return within seven days of season N+1 start; (b) join-to-first-task
   time for players joining after week 4 is within 20 percent of week-1 joiners; (c) zero reports of
   players stranded in an archived world. Failing (a) or (b) means the reset punishes rather than
   refreshes. Verdict: **defined**.
8. **Countdown.** The season end is an organizational, sports-season style timer. Announcements are
   capped at three, with no sidebar or boss-bar countdown (I3e AC4) and no per-player timer.
   Verdict: **pass**.

Structural precedent: a season is an Origins-style named bundle of UHC-style modifiers (the challenge
set), combined with the Bingo season-reset model. The Bingo reset is a confirmed precedent; the rest
is inference.

Gate: `player-psychology-designer` signs off this section before the season lifecycle is implemented
(see Open Questions).

## Risks / Trade-offs

- [Vanilla reset removes player items] → announce decision 1 in advance (I3e AC2), archive the old
  world, keep history. The I5 item 5 verdict depends on this.
- [Transition stalls on world generation at the boundary] → pre-create with `prepareLead` (I3b AC7),
  and on failure continue the current season (I3b AC4, D6 failure handling).
- [Crash between two steps repeats or skips work] → every step persists its phase first and checks
  observable state; the restart table (D6) and idempotent steps cover each gap.
- [Two active seasons] → one transaction in `switchActive` and the partial unique index.
- [Players stranded in the old world] → join routing (I3c AC2), evacuation before unload, retried unload
  (I3c AC4) and a fallback high point on teleport failure.
- [Container restart rebuilds the world service] → static CloudNet service (D9), and regeneration from
  the stored seed as a loud, non-fatal fallback.
- [Archive move across filesystems is not atomic] → `ARCHIVE_FAILED` with files untouched, admin
  alert (I3g AC2).
- [Challenge logic runs in a prepared, not yet active world] → narrowed `Predicate<World>` (D7).
- [Migration rewrites primary keys of progress tables] → `DEFAULT 'legacy'`, row-count check in the
  migration test (Test plan), and a database backup before the cut-over restart (Migration plan).
- [Scope creep into prestige or economy] → `prestigeStep` is always 0 here; currency is out of scope.

## Test plan (F.I.R.S.T.)

The tests of each task group are written before the production code they cover (red, then green).
Each test has one behaviour, a name that describes it, and no sleeps, system clock reads or shared
static state.

Unit, `core` (no server, no database, milliseconds):

- `SeasonTimelineTest`: next action for each phase combination at fixed instants; overlap and
  unknown-id rejection; no-next-season warning; announcements at 24h, 1h and 5m exactly once each.
- `SeasonServiceTest` and `SeasonTransitionTest`: in-memory fakes for `SeasonRunRepository` and
  `SeasonWorlds` (scriptable success, failure and false unload), `PlayerSeasonReset` and
  `SeasonAnnouncer`; a fixed `Clock` per step; a constant `LongSupplier`. Example names:
  `worldCreationFailureKeepsCurrentSeasonActive`, `resumeFromPreparedAfterBoundaryClosesOldSeason`,
  `challengeDeactivationIsCreditedToEndingSeason`, `secondTickDuringTransitionIsANoOp`.
- `ChallengeProgressServiceTest` and `TaskProgressServiceTest`: writes go to `CurrentSeason#id()`.
- `api` record tests: `SeasonId` pattern and length, `Season` positive duration and `end()`,
  `ProgressScope` step bounds 0 to 10.

Adapters:

- `JacksonSeasonRepositoryTest` (`@TempDir`): JSON, YAML and TOML round trip, id authority, the
  `.disabled` example is not loaded, schema file written.
- `PostgresSeasonRunRepositoryTest` (Testcontainers `postgres:16-alpine`, fresh table per test):
  save and find, `switchActive` atomicity, the partial unique index rejects a second `ACTIVE`.
- `PostgresChallengeProgressRepositoryTest` and `PostgresTaskCompletionRepositoryTest`: season
  isolation, `resetSeason` leaves other seasons, history query.
- Migration test (extend `verification/PostgresAdapterMigrationTest` from the Flyway fix): all three
  adapters migrate one database; legacy rows land in `legacy`, with row counts unchanged.
- `SeasonCommandsTest` with `RecordingCommandSource`: delegation, which `Messages.season*` fired, and
  confirm gating for `advance`, `postpone` and `reload`.

Platform (MockBukkit v4, fresh `ServerMock` per test, ticks driven with `performTicks(n)`, no sleeps):

- `BukkitSeasonWorldsTest`: evacuation moves players; unload is refused while occupied. Where MockBukkit
  does not support `createWorld`, creation is covered by the bootstrap test only.
- `SeasonJoinListenerTest`: a player with a stale world is routed; the reset happens once per season.
- `SeasonTickerTest`: a tick calls `tick(clock.instant())` with the injected clock.
- `SkyGridNgPluginTest` (bootstrap): the plugin enables with an empty `seasons/` and behaves as legacy.

Manual: one end-to-end run on a local `runServer` with a five-minute season (`PT5M`), including a kill
during `PREPARING` and during `ARCHIVING`.

ArchUnit: update `verification`. Add `seasonPersistenceAdapterIsFrameworkFree` (no `org.bukkit`, Guice
or jakarta.inject in `adapters.season`). Add a rule that `core` does not call `Clock.systemUTC()`,
`Instant.now()` or `System.currentTimeMillis()`, so time comes only through the injected `Clock`.

## Migration plan

1. Merge the Flyway per-adapter fix (PR #9) to `main`. This change does not start before it.
2. Take a database backup. The progress migrations replace primary keys.
3. Deploy the release. The migrations assign every existing row to season `legacy`.
4. Cut-over restart: the server `level-name` world is switched to the void lobby; the existing SkyGrid
   world is archived once, by hand, into `<archive.directory>/legacy/` with a `season.json` written by
   the operator, and `seasons/` gets its first season file. The first season then starts from the
   transition or from startup.
5. Rollback: revert the release and restore the database backup. The new `season_run` table and the
   progress columns are not read by the previous release, so a backup restore is the only rollback
   path for the progress migrations.

## Open Questions

- Does Paper 26.1 auto-load persisted custom dimensions at startup, and is a custom dimension folder
  self-contained enough to be moved as one unit? The restart table (D6) handles both load behaviours.
  Verify in the platform task group; the answer changes only the resume code path, not the specs.
- `player-psychology-designer` sign-off on the I5 section is required before the season lifecycle is
  implemented. It does not change the specs or the task breakdown, so it is tracked here and not as a
  task.

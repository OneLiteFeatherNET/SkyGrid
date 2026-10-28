# I3 Season lifecycle: requirements and design spec

Status: specified, not implemented. Decisions in section 7 are final. Reference state:
`origin/main` (3ad20f4). Prerequisite: the open branch `origin/fix/flyway-migration-clash` (one
Flyway location and one history table per adapter) is merged first.

Product-owner decisions this spec builds on:

- A Season has a fixed duration and its own content package: a bound challenge set plus a world
  preset (the preset carries the algorithm).
- At a season boundary a NEW SkyGrid world is created (new seed, the next season's preset). The old
  world is unloaded and archived on disk (an external BlueMap render may read it, out of scope).
- Per-player tiers and task progress are archived per season; every season starts fresh.
- I4 (prestige) and J1 (economy) are specified in `docs/design/prestige.md` and
  `docs/design/economy.md`; this spec only names their integration points (section 2.8).

## 1. User stories and acceptance criteria

Stories follow `docs/USERSTORIES.md` style. Criteria use EARS; each carries a MoSCoW tag.

### I3a: Seasons defined in config
As a **server owner**, I want each season described in one config file (dates, duration, preset,
challenge set), so that I can plan a season's content package ahead of time without touching code.

- AC1 [Must] The system shall load one season per file from `seasons/<id>.<ext>` (JSON, YAML or
  TOML, chosen by extension), and shall write `schema/season.schema.json` next to the other schemas.
- AC2 [Must] If a season file names an unknown preset or an unknown challenge id, then the system
  shall reject that season with a log line naming the file, the bad value and the known values, and
  shall keep loading the other seasons.
- AC3 [Must] If two seasons overlap in time, then the system shall reject the later-starting one with
  a log line naming both ids.
- AC4 [Must] Where the `seasons/` directory is empty or absent, the system shall behave exactly as
  today (one implicit `legacy` season, no world rotation, no scheduled transition).
- AC5 [Should] When an admin runs `/skygrid season reload confirm`, the system shall re-read
  `seasons/` and apply changes to future seasons only, never to the running season's preset or seed.

### I3b: Automatic season transition with a fresh world
As a **server owner**, I want the server to switch to the next season on schedule, with a brand new
SkyGrid world, so that each season is a clean, predictable fresh start without a manual reset.

- AC1 [Must] When the injected clock reaches the active season's end instant and a next season is
  configured, the system shall run the transition sequence of section 3.
- AC2 [Must] The system shall create the next season's world with a new seed and the next season's
  preset, under the world key `skygridng:season_<id>`.
- AC3 [Must] The system shall persist the seed and world key before creating the world, so that a
  restart during the transition recreates the identical world.
- AC4 [Must] If world creation fails, then the system shall keep the current season active and
  playable, notify online admins and the log, and retry per section 3.3.
- AC5 [Must] While a transition is in progress, the system shall resume it from its last persisted
  phase after a server restart, without repeating a completed step.
- AC6 [Must] When the transition switches seasons, the system shall activate exactly the new
  season's challenge set and deactivate every other challenge.
- AC7 [Should] The system shall pre-create the next world a configurable lead time (default 15
  minutes) before the boundary, so that the boundary itself does not stall on world generation.
- AC8 [Must] If no next season is configured when the active season ends, then the system shall keep
  the active season running and warn admins once per day until a next season exists.

### I3c: Players moved safely into the new season
As a **player**, I want to land safely in the new season's world when it starts, whether I am online
or join later, so that I never get stuck in an archived world or lose my way in.

- AC1 [Must] When the season switches, the system shall move every online player from the old season
  world to a spread-spawn slot in the new world (Epic C1/C2 rules apply).
- AC2 [Must] When a player joins whose last location is in an unloaded (archived) season world, the
  system shall place them at a spread-spawn slot in the active season world.
- AC3 [Must] When a player first enters a new season, the system shall reset their vanilla player
  state (inventory, ender chest, XP, health, hunger, effects, respawn point, game mode to survival),
  exactly once per player per season. (Scope is decision 1 in section 7.)
- AC4 [Must] If unloading the old world fails because it still holds players, then the system shall
  move the remaining players and retry, and shall never delete or move world files of a loaded world.

### I3d: Per-season progress archive
As a **player**, I want my past seasons' tiers and task completions kept, so that what I achieved is
recognized even though the new season starts from zero.

- AC1 [Must] The system shall key every tier and task-completion row by season id, and new progress
  shall always be written to the active season.
- AC2 [Must] When a season ends, the system shall keep its rows unchanged (archive by key, no copy,
  no delete).
- AC3 [Must] When a player runs `/skygrid season history [player]`, the system shall list that
  player's tier and completed task count for each past season.
- AC4 [Must] When an admin runs `/skygrid challenge tier reset confirm`, the system shall clear
  progress of the active season only (the I2a stand-in is narrowed, not removed).
- AC5 [Must] When existing data is migrated, the system shall assign all pre-season rows to the
  `legacy` season.

### I3e: Season visibility and announcements
As a **player**, I want to see which season is running and when it ends, so that I can plan my play
without being pressured by a countdown.

- AC1 [Must] When a player runs `/skygrid season info`, the system shall show the season's display
  name, its active challenges and its end date as an absolute date in the viewer's locale.
- AC2 [Must] The system shall announce an upcoming transition at most three times (24 hours, 1 hour
  and 5 minutes before), each stating that progress is archived, not lost.
- AC3 [Must] The system shall render every season message from the `en`/`de` bundles in the
  viewer's client locale (Epic D1).
- AC4 [Won't, this release] A sidebar or boss-bar countdown timer.

### I3f: Admin control over the lifecycle
As a **server admin**, I want to inspect, force, retry and postpone a transition, so that I can
handle maintenance windows and failures without editing the database.

- AC1 [Must] When an admin runs `/skygrid season status`, the system shall show the active season,
  its phase, the next season and the next scheduled action with its instant.
- AC2 [Must] When an admin runs `/skygrid season advance`, the system shall only warn and name what
  will happen; when the admin runs `/skygrid season advance confirm`, the system shall start the
  transition now (F2 convention).
- AC3 [Must] When an admin runs `/skygrid season retry`, the system shall resume a failed transition
  from its persisted phase.
- AC4 [Should] When an admin runs `/skygrid season postpone <duration> confirm`, the system shall move
  the active season's end (and shift following seasons that would overlap) and announce the new end.
- AC5 [Must] If the database is unreachable, then the system shall refuse every transition, report
  the reason in `status`, and keep the current world running.

### I3g: Archived worlds on disk
As a **server owner**, I want each finished season world kept as a self-describing folder, so that an
external renderer (BlueMap) or a later restore can use it.

- AC1 [Must] When the old world is unloaded, the system shall move its folder to
  `<archive.directory>/<seasonId>/` and write a `season.json` manifest (id, display name, seed,
  preset, start, end, world key, plugin version).
- AC2 [Must] If the archive move fails, then the system shall leave the folder in place, mark the
  run `ARCHIVE_FAILED`, and keep the new season fully playable.
- AC3 [Could] Where `archive.compress: true` is set, the system shall write a `.tar.zst` instead.

## 2. Design

### 2.1 Placement in the hexagon

| Layer | New or changed |
|---|---|
| `api.model` | `SeasonId`, `Season`, `SeasonCatalog`, `SeasonPhase`, `SeasonRun`, `SeasonStatus` |
| `api` | `SkyGridApi#currentSeason()` (read only, `Optional<Season>`) |
| `ports.in` | `SeasonAdminUseCase`, `CurrentSeason` |
| `ports.out` | `SeasonRepository` (config), `SeasonRunRepository` (Postgres), `SeasonWorlds`, `PlayerSeasonReset`, `SeasonAnnouncer`; changed `ChallengeProgressRepository`, `TaskCompletionRepository` |
| `core.service` | `SeasonTimeline`, `SeasonService`, `SeasonTransition`; changed `ChallengeProgressService`, `TaskProgressService` |
| `adapters/config` | `SeasonDtos`, `JacksonSeasonRepository`, `ConfigSchemas.Kind.SEASON` |
| `adapters/season` (new) | `PostgresSeasonRunRepository`, `SeasonRunEntity` |
| `adapters/challenge-progress`, `adapters/task-progress` | `season_id` column migrations |
| `adapters/command` | `SeasonCommands`, `Messages.season*` |
| `platforms/common` | `platforms.olf.season`: `BukkitSeasonWorlds`, `BukkitPlayerSeasonReset`, `BukkitSeasonAnnouncer`, `SeasonTicker`, `SeasonJoinListener`, `SeasonFeature`, `SeasonModule` |

Season is OLF-specific, so its Bukkit side lives in `platforms.olf.season`, mirroring challenges.

### 2.2 Domain model (`api.model`, records, validated in compact constructors)

- `SeasonId(String value)`: `[a-z0-9_]{1,64}` (same 64 bound as `ChallengeRule.MAX_ID_LENGTH`);
  constant `SeasonId.LEGACY = new SeasonId("legacy")`.
- `Season(SeasonId id, String displayName, Instant start, Duration duration, String preset,
  Set<String> challenges, OptionalLong seed)`: `duration` positive; `end()` = start + duration;
  `contains(Instant)` is start inclusive, end exclusive; `seed` empty means "generate at prepare".
- `SeasonCatalog(Map<SeasonId, Season> seasons)`: ordered by start; `next(SeasonId)`;
  `at(Instant)`.
- `SeasonPhase` enum: `PLANNED, PREPARING, PREPARED, ACTIVE, CLOSING, UNLOADING, ARCHIVING,
  ARCHIVED, PREPARE_FAILED, ARCHIVE_FAILED`.
- `SeasonRun(SeasonId id, String worldKey, long seed, SeasonPhase phase, Instant startedAt,
  Instant endedAt, int attempts)`: the persisted runtime fact about one season.
- `SeasonStatus(Season active, SeasonPhase phase, Optional<Season> next, Optional<Instant>
  nextActionAt, Optional<String> blockedReason)`: the read model for `status`.

### 2.3 Ports

`ports.in.CurrentSeason`: `SeasonId id()`, `Optional<Season> season()`. Read by the progress
services; returns `SeasonId.LEGACY` when no season is configured (I3a AC4).

`ports.in.SeasonAdminUseCase`: `SeasonStatus status()`, `List<Season> list()`,
`void tick(Instant now)` (idempotent scheduler entry), `TransitionResult advanceNow()`,
`TransitionResult retry()`, `void postpone(Duration)`, `ReloadResult reload()`,
`List<SeasonHistoryEntry> history(UUID player)`, `void addTransitionListener(SeasonTransitionListener)`.

`ports.out.SeasonRepository`: `SeasonCatalog load()` (config, read only; no save).

`ports.out.SeasonRunRepository`: `Optional<SeasonRun> find(SeasonId)`, `Optional<SeasonRun>
active()`, `List<SeasonRun> inFlight()` (phases other than ACTIVE/ARCHIVED),
`void save(SeasonRun)`, `void switchActive(SeasonId from, SeasonId to, Instant at)` (one DB
transaction: from becomes CLOSING with `ended_at`, to becomes ACTIVE with `started_at`).

`ports.out.SeasonWorlds` (Bukkit side, main-thread contract documented on the port):
`PrepareResult prepare(String worldKey, long seed, String preset)`, `boolean isLoaded(String)`,
`boolean exists(String)`, `int evacuate(String fromKey, String toKey)`, `boolean unload(String)`,
`ArchiveResult archive(String worldKey, SeasonManifest manifest)`.

`ports.out.PlayerSeasonReset`: `void resetOnline(SeasonId season)`; the per-join path is driven by
`SeasonJoinListener`.

`ports.out.SeasonAnnouncer`: `void upcoming(Season next, Instant at)`, `void started(Season)`,
`void adminAlert(AlertKind, String detail)`. Keeps message keys out of the core.

Seed source: the core takes a `LongSupplier` (bound to `SecureRandom::nextLong` in the platform,
a fixed value in tests). Time: the core takes a `java.time.Clock` (bound to `Clock.systemUTC()`).

Changed out ports (season-scoped, a breaking change inside the repo, `ports` is not published):

- `ChallengeProgressRepository`: `tierOf(SeasonId, UUID, String)`, `recordCompletion(SeasonId, UUID,
  String)`, `resetSeason(SeasonId)` (replaces `resetAll`), `List<SeasonTier> history(UUID)`.
- `TaskCompletionRepository`: keyed by `ProgressScope` plus `taskId` (section 2.6); `resetSeason(SeasonId)`;
  `Map<SeasonId, Integer> completedCountBySeason(UUID)`.
- The in ports `ChallengeProgressUseCase` / `TaskProgressUseCase` keep their current signatures;
  the services resolve `CurrentSeason#id()` per call, so the four `ChallengeBehavior`s do not change.

### 2.4 Core services

- `SeasonTimeline` (pure): validates a `SeasonCatalog` (overlap, unknown preset/challenge given the
  known id sets) and answers `nextAction(Instant now, SeasonCatalog, List<SeasonRun>)` returning one
  of `None`, `Announce(kind, at)`, `Prepare(season, at)`, `Switch(from, to, at)`, `Resume(run)`,
  `WarnNoNext`. All scheduling logic lives here and is tested with plain `Instant`s.
- `SeasonTransition`: executes one step per phase, persisting the phase before and after every side
  effect (section 3). Each step is idempotent: it first checks the observable state (world loaded,
  folder present, run phase) and skips work already done.
- `SeasonService`: implements `SeasonAdminUseCase` and `CurrentSeason`; owns the live catalog, a
  cached active `SeasonId` (refreshed on switch), the transition listeners, and a single-flight
  guard so `tick`, `advanceNow` and `retry` never run two transitions at once.
- `ChallengeProgressService` / `TaskProgressService`: pass `CurrentSeason#id()` to the repositories.
  `resetAll()` on the in port is renamed `resetCurrentSeason()`.

### 2.5 Config adapter: `seasons/<id>.<ext>`

Same rules as `challenges/`: one file per season, id in the file is authoritative, format by
extension, bundled example seeded only when `seasons/` is absent (an empty directory stays empty, so
an operator can opt out). `JacksonSeasonRepository` sits beside `JacksonChallengeRepository`;
`SeasonDtos.SeasonDto` feeds `ConfigSchemas` (new `Kind.SEASON` writing
`schema/season.schema.json`). `config.<ext>` gains an optional `season` block.

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

```yaml
# config.yml (new optional block)
season:
  prepareLead: PT15M
  archive:
    directory: skygrid-archive   # relative to the server root, or absolute
    compress: false
```

Bundled default ships no active season: the seeded example is written as
`seasons/example.yml.disabled` (not a supported extension, so it is not loaded) to keep I3a AC4.

### 2.6 Persistence adapter `adapters/season` (Postgres)

`PostgresSeasonRunRepository extends AbstractJpaRepository` with persistence unit `season`,
location `classpath:db/migration/season`, history table `flyway_history_season` (pattern from the
flyway-clash branch). Same lazy pool, `DatabaseConfig::fromEnvironment`, `hbm2ddl=validate`.

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

No player data in this table. Season definitions stay in config (single source of truth); the table
records only what happened at runtime.

### 2.6a Shared progress scope key

Tiers and task completions share one scope key, defined here once so that the season work and the
prestige work (`docs/design/prestige.md`) migrate the progress tables a single time:

```java
// api.model: season, player, challenge and prestige step, validated in the compact constructor
public record ProgressScope(SeasonId season, UUID playerId, String challengeId, int prestigeStep) {
    // 0 <= prestigeStep <= 10 (PrestigeLadder.MAX_STEPS); challengeId follows ChallengeRule's id rules
}
```

Task completion is keyed by `ProgressScope` plus `taskId`. The season tier counter is season-wide and is
keyed by the scope without its step (`season_id, player_id, challenge_id`), so a prestige step does
not split a tier. Prestige steps are not part of this slice's behaviour: every write uses step 0 until
the prestige use case exists, and the column is ready for it.

Migrations in the existing adapters (archive by key, I3d):

- `db/migration/challenge-progress/V3__scope_tier_by_season.sql`: add `season_id TEXT NOT NULL
  DEFAULT 'legacy'` with the 64 check, backfill is implicit, drop the default, replace the primary
  key with `(season_id, player_id, challenge_id)`.
- `db/migration/task-progress/V2__scope_completion_by_season_and_step.sql`: the only migration of the
  task completion table in the whole plan. Add `season_id TEXT NOT NULL DEFAULT 'legacy'` (64 check)
  and `prestige_step SMALLINT NOT NULL DEFAULT 0 CHECK (prestige_step BETWEEN 0 AND 10)`, drop both
  defaults, replace the primary key with `(season_id, player_id, challenge_id, prestige_step, task_id)`.
  Existing rows become season `legacy`, step 0, so no player loses a completion.

### 2.7 Platform wiring (`platforms.olf.season`)

- `SeasonModule` (installed from `SkyGridNgPlugin.modules()`): binds the ports above, `Clock`,
  the seed `LongSupplier`, `SeasonService`, and adds `SeasonFeature` to the `OlfFeature` set and
  `SeasonCommands` to the `CloudCommandHandler` set (via `CommandModule` `@Provides`).
- World creation (`BukkitSeasonWorlds`): `WorldCreator.ofKey(NamespacedKey.fromString(worldKey))`
  `.seed(seed).generator(worldGenerators.forWorld(key, preset)).environment(NORMAL)`, plus
  `forcedSpawnPosition(...)` (new in 26.1) so Paper skips its spawn search in the void; then
  `Bukkit.createWorld(creator)`. A `null` result or exception becomes `PrepareResult.Failed`.
  Since 26.1 custom world keys are persisted and dimensions live under `world/dimensions/<ns>/<key>/`.
- Thread and tick rules: create, unload and teleport run on the main thread via the scheduler and
  only when `Bukkit.isTickingWorlds()` is false (Javadoc warns against mid-tick un/loading); the
  archive file move and manifest write run async after a successful unload.
- The season world is never the server's main (`level-name`) world, because the main world cannot be
  unloaded. The main world becomes a small non-season world (section 7, decision 3).
- Startup: the plugin loads at `STARTUP`, before any world exists, so `SeasonFeature` does not create
  worlds in `onEnable`. It listens for `ServerLoadEvent` (type `STARTUP`) and then calls
  `SeasonAdminUseCase#tick`, which (re)loads the active season world with `createWorld` (a load when
  the folder exists) and resumes any in-flight run.
- Scheduling: `SeasonTicker` is a `runTaskTimer` every 600 ticks (30 s) calling `tick(clock.instant())`;
  all due-time math is in `SeasonTimeline`, so the ticker holds no logic. Database calls inside `tick`
  are read on an async task and the resulting world step is handed back to the main thread.
- Joins: `SeasonJoinListener` on `PlayerSpawnLocationEvent` (the event `SpreadSpawnListener` already
  uses) routes a player whose spawn world is not the active season world into it, and applies the
  one-time reset keyed by a PDC marker `skygridng:season = <id>` on the player.
- Challenge scoping: the existing `Predicate<World>` (generator is `HexagonalChunkGenerator`) still
  matches; it narrows to "is the active season world" when seasons are configured, so a prepared but
  not yet active world never triggers challenge logic.
- Commands (`SeasonCommands`, Cloud annotations, `CommandSource`, no Bukkit):
  `season info` (perm `skygridng.season`, default true), `season history [player]`
  (`skygridng.season`; other players need `skygridng.admin`), `season list|status|retry`,
  `season advance [confirm]`, `season postpone <duration> confirm`, `season reload [confirm]`
  (all `skygridng.admin`). Suggestion provider `season-ids` registered in `CloudCommandRegistrar`.
- Messages: `Messages.season*` factory methods, keys `season.info`, `season.history.entry`,
  `season.status`, `season.announce.upcoming`, `season.announce.started`, `season.advance.confirm`,
  `season.transition.failed`, `season.no_next`, ... in `Bundle_en.properties` and
  `Bundle_de.properties`; dates rendered with the viewer locale.
- CloudNet: a dynamic CloudNet service is rebuilt from its template on every start, which would drop
  the season world, the archive and the plugin data folder. The service must be **static** (or mount a
  persistent volume for `world/` and the archive directory). Do not sync worlds back into templates.
  The Postgres `season_run` row is authoritative: if the active season's folder is missing at startup,
  the plugin regenerates the world from the stored seed (terrain identical, builds lost) and raises a
  loud admin alert instead of failing to start. The archive directory is the hand-off point for the
  external BlueMap job (a shared read-only mount is the simplest contract).

### 2.8 Integration points for I4 and J1 (not designed here)

- `SeasonTransitionListener#onSwitched(SeasonId from, SeasonId to)` fires after the switch commit and
  before players are moved: I4 can snapshot or reset prestige state, J1 can decide balance carry-over.
- `CurrentSeason#id()` is the key both epics should use for season-scoped rows (same pattern as I3d).
- `SkyGridApi#currentSeason()` exposes the season to other plugins on the network.
- The vanilla reset (I3c AC3) clears inventories, which interacts with any item-backed currency; J1
  must hold currency in Postgres, never in items (balances are season-scoped, section 7, decision 2).

## 3. Season transition sequence

### 3.1 Happy path (current season A, next season B)

1. **Announce** (T-24h, T-1h, T-5m): `SeasonAnnouncer#upcoming`. No state change.
2. **Prepare** (T - `prepareLead`): resolve seed (config value or `LongSupplier`); persist
   `season_run(B, world_key, seed, PREPARING)`; on the main thread `SeasonWorlds#prepare`; on success
   persist `PREPARED`. Players cannot reach B yet.
3. **Close A** (T): deactivate A's challenges through `ChallengeAdminUseCase` (A's `CLOSING` phase
   is written by step 4's transaction). This fires the existing round-boundary hooks
   (`one_life` survivor tiers, task completions) while `CurrentSeason` still returns A, so the final
   round is credited to A.
4. **Switch** (one DB transaction, `switchActive(A, B, now)`): A `CLOSING` with `ended_at`, B
   `ACTIVE` with `started_at`. Refresh the cached `CurrentSeason`. From here every write goes to B.
5. **Activate B's challenges**: activate exactly B's set, deactivate the rest; fire
   `SeasonTransitionListener`s; `SeasonAnnouncer#started`.
6. **Move players** (main thread): `evacuate(A, B)` teleports each player in A to a spread slot in B;
   `PlayerSeasonReset` runs on arrival (PDC marker prevents a second reset).
7. **Unload A**: persist A `UNLOADING`; `unload(A)` with save=true, only when not ticking worlds.
8. **Archive A** (async): persist `ARCHIVING`; move folder, write `season.json`; persist `ARCHIVED`.

### 3.2 Restart in the middle

On `ServerLoadEvent`, `tick` reads `inFlight()` and resumes by phase:

| Persisted state | Resume action |
|---|---|
| B `PREPARING` | `exists(B)`? load it with the stored seed : create it with the stored seed; then `PREPARED` |
| B `PREPARED`, before T | load B (no players); wait for T |
| B `PREPARED`, after T | continue at step 3 (challenge deactivation is idempotent) |
| B `ACTIVE`, A `CLOSING` | load B; skip 3 to 5 except re-applying B's challenge set (idempotent); go to step 6 (joins route via `SeasonJoinListener`) then 7 |
| A `UNLOADING` | if A was auto-loaded by the server, evacuate and unload; else go to 8 |
| A `ARCHIVING` | archive is a move: if the target exists and the source does not, mark `ARCHIVED`; if both exist, mark `ARCHIVE_FAILED` for an admin |

Because the switch is one transaction, there is never a moment with two or zero `ACTIVE` seasons
(enforced by the partial unique index).

### 3.3 Failure handling

- **World creation fails** (null, exception, out of disk): B becomes `PREPARE_FAILED`,
  `attempts + 1`; A stays `ACTIVE` with its challenges untouched (step 3 never ran). Automatic retry
  after 5 minutes, at most 3 attempts, then only `/skygrid season retry`. Admins get an alert on
  join and in `status`. If T passes while B is not `PREPARED`, A simply continues (AC8 behavior) and
  players see "the next season is delayed" once, not repeatedly.
- **Database down**: no step runs (every step persists first); the world keeps running; `status`
  shows the reason. Progress writes already fail loudly per I2a today.
- **Player joins during steps 6 and 7**: `SeasonJoinListener` routes them into B, so A empties.
  `unload` returning false is retried every tick (max 200 ticks) after a fresh `evacuate`.
- **Teleport fails** (chunk load error): fall back to the C2 high point in B, never leave the player
  in A.
- **Archive move fails**: `ARCHIVE_FAILED`, files untouched, B fully playable; admin alert.
- **Server stops during step 2 world creation**: the half-written folder is reused on resume, since
  the seed is identical and Paper loads an existing folder rather than regenerating it.

## 4. I5 dark-pattern review (challenge-season-system checklist)

Mechanic in one sentence: fixed-length seasons that reset the world and per-player progress to zero
on a published date, archiving past achievements, to give everyone a fair, fresh start and a
predictable content cadence.

1. **Octalysis drives**: Development/Accomplishment (tiers, tasks), Epic Meaning (season themes),
   Empowerment (fresh world exploration), light Scarcity (season is time-boxed). No
   Unpredictability; Loss/Avoidance is present only as "progress resets". Restated without loss: the
   reset is framed and implemented as an archive ("your Season 1 record is kept"), history is
   viewable. Verdict: **pass**.
2. **Full transparency**: start, end, preset, challenges and the reset scope are all in
   `season info` with absolute dates, and announcements state exactly what resets. Nothing relies on
   the player misjudging timing or cost. Verdict: **pass**.
3. **Bartle types**: Achievers (tasks, tiers), Explorers (a new seed and preset each season),
   Socializers (everyone restarts together), Killers (none; no PvP mechanic is added). The season
   does not hurt Killers, it simply does not target them. Verdict: **pass**, note for
   `game-designer`.
4. **Reward adaptation**: this mechanic changes no reward rate or magnitude, on any signal. No
   compensation loop. Verdict: **pass** (I4 must re-run this item for prestige).
5. **Late-join and irregular players**: the Hypixel Bingo precedent applied directly: everyone starts
   from zero at season start, so a late joiner loses time, not relative standing; nothing earned is
   taken away, it is archived and shown in `history`. Risk: the vanilla reset (decision 1) removes items
   built up in the old world. Verdict: **pass, conditional on decision 1 being announced**.
6. **Skeptical observer**: "The server wipes your stuff every eight weeks" is a plausible one-line
   criticism. Mitigations: wipes are announced at season planning time, the old world is archived
   (viewable via BlueMap), history is kept. Verdict: **pass with mitigation**; no artificial urgency
   (no "last chance" offers, no purchasable carry-over).
7. **Falsifiable metric**: for `playtest-qa-coordinator`: (a) share of players active in the last 2
   weeks of season N who return within 7 days of season N+1 start, target at least 60%;
   (b) join-to-first-task time for players joining after week 4 within 20% of week-1 joiners;
   (c) zero reports of players stranded in an archived world. Failing (a) or (b) means the reset
   punishes rather than refreshes. Verdict: **defined**.
8. **Countdown**: the season end is an organizational, sports-season style timer. Announcements are
   capped at three, no sidebar or boss-bar countdown (I3e AC4 is Won't), no per-player timer.
   Verdict: **pass**.

Structural precedent: season = Origins-style named bundle of UHC-style modifiers (challenge set)
plus the Bingo season-reset model; confirmed-live precedent for Bingo resets, the rest is inference.
Reviewer: `player-psychology-designer` signs this section off before the season lifecycle is implemented.

## 5. Test plan (F.I.R.S.T.)

Unit, `core` (no server, no DB, milliseconds):

- `SeasonTimelineTest`: next action for every phase combination with fixed `Instant`s; overlap and
  unknown-id rejection; no-next-season warning; announcement instants (24h/1h/5m) exactly once each.
- `SeasonServiceTest` / `SeasonTransitionTest`: in-memory fakes for `SeasonRunRepository`,
  `SeasonWorlds` (scriptable success/failure/false-unload), `PlayerSeasonReset`, `SeasonAnnouncer`;
  `Clock.fixed` advanced by building a new fixed clock per step (no mutable static clock); seed from a
  constant `LongSupplier`. One behaviour per test, for example
  `worldCreationFailureKeepsCurrentSeasonActive`, `resumeFromPreparedAfterBoundaryClosesOldSeason`,
  `challengeDeactivationIsCreditedToEndingSeason`, `secondTickDuringTransitionIsANoOp`.
- `ChallengeProgressServiceTest` / `TaskProgressServiceTest`: writes go to `CurrentSeason#id()`.
- `api` record tests: `SeasonId` pattern and length, `Season` positive duration and `end()`, `ProgressScope` step bounds.

Adapters:

- `JacksonSeasonRepositoryTest` (`@TempDir`): JSON/YAML/TOML round trip, id authority, `.disabled`
  example not loaded, schema file written.
- `PostgresSeasonRunRepositoryTest` (Testcontainers `postgres:16-alpine`, fresh tables per test via
  unique season ids or truncate in `@BeforeEach`): save/find, `switchActive` atomicity, partial unique
  index rejects a second ACTIVE.
- `PostgresChallengeProgressRepositoryTest` / `PostgresTaskCompletionRepositoryTest`: season
  isolation, `resetSeason` leaves other seasons, history query.
- Migration test (extend `verification/PostgresAdapterMigrationTest` from the flyway branch): all
  three adapters migrate one database; legacy rows land in `legacy`.
- `SeasonCommandsTest`: `RecordingCommandSource`, asserts delegation and which `Messages.season*`
  fired, confirm gating for `advance`, `postpone`, `reload`.

Platform (MockBukkit v4, fresh `ServerMock` per test, ticks driven with `server.getScheduler()
.performTicks(n)`, no sleeps):

- `BukkitSeasonWorldsTest`: evacuate moves players, unload refused while occupied (verify what
  MockBukkit supports for `createWorld`; where it does not, cover create via the bootstrap test only).
- `SeasonJoinListenerTest`: player with a stale world is routed; reset happens once per season.
- `SeasonTickerTest`: a tick calls `tick(clock.instant())` with the injected clock.
- `SkyGridNgPluginTest` (bootstrap): plugin enables with an empty `seasons/` and behaves as legacy.

Manual: one end-to-end run on a local `runServer` with a 5-minute season (duration `PT5M`),
including a kill during `PREPARING` and during `ARCHIVING`.

ArchUnit: **yes, update**. Add `seasonPersistenceAdapterIsFrameworkFree` (no `org.bukkit`, Guice,
jakarta.inject in `adapters.season`) and extend `apiPortsAndCoreAreFrameworkFree` coverage implicitly
(new packages fall under existing rules). Add a rule that `core` does not call
`Clock.systemUTC()`/`Instant.now()`/`System.currentTimeMillis()` (time only through the injected
`Clock`), which locks in Repeatable for every future core test.

## 6. Delivery

The delivery plan for seasons, prestige and economy is one ordered list in
[`docs/design/README.md`](README.md#delivery-plan). Slice titles and order are defined there.

## 7. Decisions

The product owner resolved the open questions of this spec as follows. These decisions are final for
the first release.

1. **Vanilla player state at a season start (I3c AC3).** Inventory, ender chest, XP, health, hunger,
   effects, respawn point and game mode are reset on the first entry to each season. Everything
   resets per season: inventory, ender chest, XP, effects, game mode, tiers, task progress, the
   current prestige step and the currency balance (the balance reset is an audited `SEASON_RESET`
   ledger entry, see `docs/design/economy.md`). Archived data stays under its season id. The highest
   prestige step ever reached is kept as an all-time record.
2. **Economy across seasons (J1).** Currency does not carry over. Balances are season-scoped, and the
   closing season writes one `SEASON_RESET` ledger entry per account. Open market listings are closed
   into the mailbox at season end. Mailbox contents expire with the season under the same inventory
   policy as decision 1.
3. **Main world.** The server `level-name` is a small static void lobby. Players join directly into
   the active season world. The existing SkyGrid world becomes season `legacy` at the cut-over and is
   archived once, by hand, at the cut-over restart.
4. **Gap between seasons (I3b AC8).** If no next season is configured at the end instant, the current
   season is extended, and admins get a daily warning until a next season exists.
5. **Prestige per season (I4).** The current prestige step is season-scoped like tiers. The highest
   step ever reached is kept as an all-time record (see `docs/design/prestige.md`).
6. **Servers per database.** One SkyGrid service per database. A realm column is added only if
   parallel SkyGrid services are ever run.
7. **Shared progress scope key.** This spec owns the scope key `ProgressScope` (season, player,
   challenge, prestige step, section 2.6). The prestige spec consumes its `prestigeStep` component.
   The task completion table is migrated once.

## Sources

- Paper 26.1.2 Javadoc: `Bukkit.unloadWorld`, `Bukkit.createWorld`, `WorldCreator.ofKey`
  (custom world keys persisted since 26.1), via Context7 `/websites/jd_papermc_io_paper/26.1.2`.
- [Paper 26.1 release notes](https://papermc.io/news/26-1): dimensions under `world/dimensions/`,
  per-world `paper-world.yml` moved, world keys over names, `WorldCreator#forcedSpawnPosition`, no
  downgrade after upgrade.
- To verify in the platform slice: whether Paper 26.1 auto-loads persisted custom dimensions at startup (the
  resume table handles both cases) and whether a custom dimension folder is self-contained enough to
  be moved as one unit for the archive.

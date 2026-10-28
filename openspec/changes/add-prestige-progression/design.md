# Design

## Context

The challenge system ships `ChallengeRule` and `ChallengeCatalog` (see `proposal.md`, Impact). Each
challenge has one `ChallengeBehavior` implementation in `platforms/common`. Player progress is
persisted per season by `add-season-lifecycle`, which defines the season ids and the `ProgressScope`
key `(season, player, challenge, prestige step)`. This change consumes that key. The seasons change
adds `season_id` and `prestige_step` (0 for every existing row) to the task completion table, so
prestige only needs to pass the player's current step into that key.

Constraints that shape the design:
- Hexagonal rules: `api`, `ports`, `core` and the framework-free adapters never import Bukkit, Guice,
  Jackson or jakarta.inject. Bukkit code stays in `platforms/common`.
- Block names are namespaced `BlockId` values in config, never Bukkit `Material` names.
- Persistence follows the one-folder, one-history-table rule per adapter.
- Behavior signals (session length, completion ratio, drop-off, return rate, deaths) are not used.
- The I5 gate (`.claude/skills/challenge-season-system/SKILL.md`) applies to every mechanic in this change.

## Goals / Non-Goals

**Goals:**
- A typed, bounded, previewable set of difficulty modifiers per challenge step, defined by admins.
- Player-controlled raise (confirmed, gated by completed tasks) and lower (free, any time).
- Exact per-player application of modifiers, visible in sidebar, join line and `info`.
- Completion records and reward grants that stay correct across raises and lowers.
- Invalid admin configuration disables prestige for one challenge, never the whole load.

**Non-Goals:**
- Season lifecycle, season worlds and the task completion migration (`add-season-lifecycle`).
- Economy, currency rewards, and per-step reward overrides (I4-25).
- Behavior-signal suggestions, automatic step changes and cross-challenge prestige (I4-25).
- Enforcement of `level_is_border`'s `blocks_per_level` (display only today; no default ladder ships).
- A public prestige leaderboard (needs its own I5 review).

## Decisions

### D1. Domain model in `api.model`
- `sealed interface DifficultyModifier` with records `MaxHealth(int halfHearts)` (bounds 2..20),
  `DamageTakenMultiplier(double multiplier)` (1.0..3.0), `BlockedRegainSources(Set<RegainSource>)`
  (non-empty subset of `SATIATED`, `REGEN_EFFECT`, `INSTANT_HEALTH`), `ExhaustionMultiplier(double)`
  (1.0..3.0), `DeniedStations(Set<BlockId>)` (1..16 entries), `BlocksPerLevel(int perLevel)` (1..10).
- Each record validates its bounds in a compact constructor and offers
  `boolean atLeastAsHardAs(DifficultyModifier previous)` for the monotonic rule. Consumers use an
  exhaustive `switch` without `default`, so a new modifier type fails the build instead of silently
  doing nothing.
- `enum RegainSource { SATIATED, REGEN_EFFECT, INSTANT_HEALTH }` is platform-neutral. The platform maps
  it to Bukkit regain reasons, so `api` does not know Bukkit.
- `record PrestigeStep(int step, List<DifficultyModifier> modifiers)`: `step >= 1`, non-empty, one
  modifier per type, `List.copyOf`.
- `record PrestigeLadder(List<PrestigeStep> steps, boolean announceUnlock)`: `MAX_STEPS = 10`, steps
  numbered 1..n without gaps, each step at least as hard as the previous one for every shared modifier
  and strictly harder in at least one. `static empty()`, `Optional<PrestigeStep> step(int)`,
  `int maxStep()`.
- `ChallengeRule` gains a sixth component `PrestigeLadder prestige` (`null` maps to `empty()`). The
  existing constructors keep compiling and default to `empty()`.
- `record PlayerPrestige(SeasonId season, UUID playerId, String challengeId, int currentStep, int highestStep)`
  with `0 <= currentStep <= highestStep <= 10` and the challenge id rules of `ChallengeTier`.

Alternative considered: one generic `Map<String, Object>` per step. Rejected: no compile-time check
of bounds, and the config schema could not describe it.

### D2. Modifier table per challenge
Step 0 is always the challenge as it ships today. A modifier absent from a step inherits nothing: each
step lists its full set, so the config reads the same as the preview.

| Challenge | Modifier `type` | Effect at step > 0 | Hard bounds | Monotonic rule |
|---|---|---|---|---|
| `one_life` | `max_health` (`halfHearts`) | Sets max health via a keyed attribute modifier, removed on step 0, inactive or world change | 2..20 | non-increasing |
| `one_life` | `damage_taken_multiplier` (`multiplier`) | Multiplies final damage taken | 1.0..3.0 | non-decreasing |
| `no_natural_regeneration` | `blocked_regain_sources` (`sources`) | Cancels these regain sources in addition to `SATIATED` | subset of the three sources | superset of previous |
| `no_natural_regeneration` | `exhaustion_multiplier` (`multiplier`) | Multiplies hunger exhaustion | 1.0..3.0 | non-decreasing |
| `no_crafting_table` | `denied_stations` (`blocks`) | Denies these blocks in addition to `minecraft:crafting_table`, same violation tracking | 1..16 `BlockId`s | superset of previous |
| `level_is_border` | `blocks_per_level` (`perLevel`) | Lowers the displayed radius factor (hardcoded 10 today) for that player's action bar | 1..10 | non-increasing |

Bundled defaults (adjustable by the admin in the challenge files, not hardcoded):
- `one_life`: 3 steps (16, 12, 12 half-hearts; multipliers 1.0, 1.0, 1.25).
- `no_natural_regeneration`: 2 steps (`regen_effect`; then plus exhaustion 1.5).
- `no_crafting_table`: 2 steps (`furnace`, `smithing_table`; then plus `anvil`, `enchanting_table`).
- `level_is_border`: no ladder. It is display-only today, so a "harder" value nothing enforces would be
  fake difficulty. The modifier type exists for a later enforcement story.

### D3. Ports
- `ports.out.PrestigeRepository`: `find(SeasonId, UUID, String)` returns `Optional<PlayerPrestige>`
  (absent means step 0), `save(PlayerPrestige)` is an upsert returning `SaveOutcome` (`Saved` or
  `Conflict`, see D5), `resetSeason(SeasonId)`, and `allTimeHighest(UUID, String)` returns the maximum
  `highestStep` over all seasons. The port exposes domain records and outcomes only.
- `ports.out.TaskCompletionRepository` is consumed from `add-season-lifecycle`, where `isCompleted` and
  `markCompleted` take the `ProgressScope`. This change adds one read, `isCompletedAtAnyStep(SeasonId, UUID,
  String taskId)`, for the reward rule (D4). No schema change: it reads the same table.
- `ports.in.PrestigeUseCase`: `statusOf`, `raise`, `lower`, `activeModifiers` (hot path), `forget`
  (quit), `resetCurrentSeason`, and `addChangeListener(PrestigeChangeListener)`.
- `sealed interface PrestigeChange` with `Raised`, `Lowered`, `TasksMissing(List<Task>)`, `AtMaximum`,
  `AtMinimum`, `ChallengeRunning`, `NoLadder`, `UnknownChallenge`, `ConcurrentChange` (a second lost race on
  the same row, see D4). Returning a result type keeps
  rejections explicit and testable, and lets the command adapter map each case to one message.
- `ports.in.TaskProgressUseCase` keeps its signatures. `tasksFor` and `completeTask` resolve the
  player's current step inside the core.

The port has no input for clock, session, death count or any behavior metric. I4-11 holds by
construction.

### D4. Core services
- `core.state.PrestigeLevels`: reads through `PrestigeRepository` and keeps a `ConcurrentHashMap` cache
  of `PlayerPrestige` per (player, challenge). It is cleared on season switch and evicted on quit.
- `core.service.PrestigeService` implements `PrestigeUseCase`. Order of checks for `raise`: unknown
  challenge, no ladder, at maximum, challenge active, missing tasks at the current step. Then save and
  notify listeners. `lower` checks only unknown challenge and at minimum.
- Concurrency (Optimistic Lock, one retry): a `Conflict` from `save` means another request changed the same
  row first. `PrestigeService` drops the cached entry, reloads the row through the repository, and runs the
  check order again once. A second `Conflict` returns `PrestigeChange.ConcurrentChange`, which the command
  maps to one "try again" message and changes nothing. This is the existing rejection style: an explicit
  `PrestigeChange` case per outcome, not an exception reaching the player. The same rule applies to `lower`.
- `core.service.TaskProgressService` (changed) takes `PrestigeLevels` and passes the current step into
  the completion repository.
- Reward once per task (I4-10): `TaskProgressService` grants a task's reward only when
  `isCompletedAtAnyStep` is false for that season, player, challenge and task, checked before the new row is
  written. A completion at any higher step therefore grants nothing, and the currency credit key
  `task:<seasonId>:<challengeId>:<taskId>:<playerId>` has no step, matching the wallet. Step-scoped rows keep
  progress only.
- Break the cycle: prestige eligibility needs task status, and task status needs the current step. The
  shared `PrestigeLevels` state is read by both services, so neither depends on the other.
- Unlock notice (I4-16): `PrestigeService` listens to task completion. When a completion closes the
  current step, it fires an unlock notification. Idempotency comes from the completion event itself,
  which only fires once per (step, task), so no extra column is needed.

Why the step is non-increasing during a round: raising is blocked while the challenge runs, and lowering
is allowed. A player therefore cannot play easy and raise before the round ends to inflate the credit.

### D5. Adapters
Config (`adapters/config`):
- `ChallengeDtos.ChallengeDto` gains an optional `PrestigeDto`. The modifier DTO is flat and
  discriminated by `type`, like `RewardDto`. The integer for `blocks_per_level` is named `perLevel`, so
  it does not clash with `denied_stations.blocks`.
- `JacksonChallengeRepository` maps DTOs to the model. A record-constructor `IllegalArgumentException`
  for one ladder is caught per file, logged with the file and step, and the challenge loads with
  `PrestigeLadder.empty()`.
- The schema is generated from the DTOs through `ConfigSchemas`. Bounds go into
  `@JsonPropertyDescription`, so editors show them.

Persistence (`adapters/prestige`, new module, package `adapters.prestige`):
- `PostgresPrestigeRepository extends AbstractJpaRepository` (the shared base in `adapters/persistence`) with
  `super("prestige", "classpath:db/migration/prestige", "flyway_history_prestige", configSupplier)`.
  Data Mapper: `PlayerPrestigeEntity` is the only JPA class, keyed by an `@EmbeddedId` record
  `PlayerPrestigeId(seasonId, playerId, challengeId)`; a mapper converts entity and `PlayerPrestige` record
  both ways, so the domain never sees a JPA type. The season id uses the shared `SeasonIdConverter` from
  `add-season-lifecycle`. Its own `META-INF/persistence.xml` with `hibernate.hbm2ddl.auto=validate`.
- Migration `db/migration/prestige/V1__create_player_prestige.sql`:

```sql
CREATE TABLE player_prestige (
    season_id    TEXT NOT NULL CHECK (char_length(season_id) <= 64),
    player_id    UUID NOT NULL,
    challenge_id TEXT NOT NULL CHECK (char_length(challenge_id) <= 64),
    current_step SMALLINT NOT NULL CHECK (current_step >= 0 AND current_step <= 10),
    highest_step SMALLINT NOT NULL CHECK (highest_step >= current_step AND highest_step <= 10),
    version      BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (season_id, player_id, challenge_id)
);
```

- Only counters are stored: no timestamps, no history, no behavior data. Row absence means step 0.
  Season rows are archived with their season and never deleted by this change.
- `@Version long version` on every row (Optimistic Lock). A raise or lower loads the row, changes it and
  flushes; a stale copy fails with `OptimisticLockException`, which `save` returns as `Conflict` (D4). A duplicate
  first insert of the same key from two requests fails on the primary key, Hibernate's
  `ConstraintViolationException`, and is returned as `Conflict` as well.
- `resetSeason` is a named bulk update that sets `currentStep` to 0 and increments `version` in the same
  statement, so a request that still holds the old row cannot overwrite the reset: its save fails as `Conflict`.
- The all-time highest step is a named query that returns the maximum `highestStep` over all seasons of a
  player and challenge.

Commands (`adapters/command`): a new `PrestigeCommands implements CloudCommandHandler`, bound in
`CommandModule`, shaped like `ChallengeCommands`. Console senders are refused by the same guard as
other handlers. A new suggestion provider in `CloudCommandRegistrar` lists challenges with a non-empty
ladder.

| Command | Permission | Result |
|---|---|---|
| `skygrid prestige info [challenge]` | `skygridng.prestige` (default true) | I4-14; without argument, one line per challenge with a ladder |
| `skygrid prestige up <challenge>` | `skygridng.prestige` | preview and click-to-confirm |
| `skygrid prestige up <challenge> confirm` | `skygridng.prestige` | `raise`, one message per `PrestigeChange` case |
| `skygrid prestige down <challenge>` | `skygridng.prestige` | `lower` |
| `skygrid prestige of <player> [challenge]` | `skygridng.admin` | I4-22 |
| `skygrid prestige reset` / `reset confirm` | `skygridng.admin` | I4-23, F2 convention |

`info` is a literal subcommand, not an optional argument on `prestige` itself, to avoid a literal and
variable sibling clash in Cloud.

Scoreboard (`adapters/scoreboard`): `ChallengeSidebarListener` takes `PrestigeUseCase`. The title gets
a ` [P<n>]` suffix and the top line shows the modifier summary when the step is above 0. It subscribes
to `addChangeListener` and rebuilds only that player's board. Task updates keep their in-place path.

### D6. Platform wiring (`platforms/common`)
- `RepositoryModule` provides `PrestigeRepository` as a singleton backed by
  `PostgresPrestigeRepository(DatabaseConfig::fromEnvironment)`, plus `PrestigeLevels`, `PrestigeService`
  bound to `PrestigeUseCase`, and the changed `TaskProgressService`.
- `ChallengeBehavior` gains `default Set<Class<? extends DifficultyModifier>> supportedModifiers()`
  returning an empty set. Each behavior overrides it. `ChallengeFeature.enable()` disables any ladder
  that uses an unsupported type, with a warning (I4-21).
- Behaviors read `activeModifiers(player, challengeId)` only after their existing active and world
  guards. `OneLifeListener` adds a damage handler for the multiplier and applies or removes a keyed
  `AttributeModifier` on `MAX_HEALTH` on join, world change, catalog change and prestige change.
  `NoNaturalRegenerationListener` widens its reason check and adds an exhaustion handler.
  `NoCraftingTableListener` checks a per-player set of `Material` resolved from `BlockId`, dropping
  unknown ids with a warning. `LevelBorderWarningTask` computes the radius per recipient.
- New `PrestigeFeature` (`OlfFeature`, bound in `OlfModule`): sends the I4-13 join line, calls `forget`
  on quit, and relays unlock, raise and lower notices.
- Messages: `messages/Bundle_en.properties` and `Bundle_de.properties` get `skygridng.command.prestige.*`
  keys (`info`, `preview`, `preview.fresh_tasks`, `confirm_hint`, `raised`, `lowered`, `tasks_missing`,
  `at_maximum`, `at_minimum`, `challenge_running`, `no_ladder`, `console`, `of`, `reset`,
  `reset.confirm`), `skygridng.prestige.*` keys (`join_summary`, `unlocked`, `sidebar_suffix`), and one
  `skygridng.prestige.modifier.<type>` key per modifier type. All modifiers are described through the
  same summary text in preview, info, join line and sidebar.

### D7. Season interplay
Decided by the product owner: the current step resets with the season (every player starts a new
season at step 0), and the highest step is kept as an all-time record across seasons. Season rows are
archived with their season.

## Risks / Trade-offs

- [Admin sets a step that is too hard] → Hard bounds and monotonic checks in the model; an invalid
  ladder disables prestige for that challenge only; the preview shows exact modifiers before a raise.
- [Players lower mid-round to dodge a raised penalty] → Lowering is allowed by design (player agency,
  no lock-in); records are kept, so lowering costs nothing that could be farmed.
- [A raise inflates round-end credit] → Raising is blocked while the challenge runs, so the step is
  non-increasing during a round.
- [Modifier applied to the wrong player] → The per-player `activeModifiers` lookup is the only source;
  MockBukkit tests cover one stepped and one unstepped player in the same challenge.
- [Attribute modifier left behind after a step change or world change] → Keyed `AttributeModifier`
  removed on step 0, deactivation, world change and quit; a test asserts max health is restored.
- [Cache returns a stale step after a change made elsewhere] → The cache is per process and is updated
  on every write through `PrestigeService`; the season switch clears it. The change listener refreshes
  the sidebar and join state.
- [A new modifier type is silently ignored by a behavior] → Exhaustive `switch` without `default`, and
  `supportedModifiers()` checked at feature enable.
- [Unlock message becomes nagging] → Sent once per player, challenge and step, never on later joins,
  and disabled per challenge by `prestige.announceUnlock: false`.
- [Concurrent raise and lower of the same row] → `@Version`; the loser gets `Conflict`, reloads once, and a
  second loss returns `ConcurrentChange` (D4). Nothing is written by the losing request.
- [Migration of existing completions] → Owned by `add-season-lifecycle`; this change only consumes step 0
  for existing rows.
- [Dependency on `add-season-lifecycle`] → This change is implemented after that change is merged. Its
  season ids and progress scope key are read from the merged code, not copied.

## I5 dark-pattern review

Checklist from `.claude/skills/challenge-season-system/SKILL.md`, applied item by item. Result per item,
with the design evidence.

1. **Octalysis drives.** Accomplishment (visible step and highest step) and empowerment (the player
   chooses and can lower). Prestige adds no new loss: lowering is free and keeps every record. The season
   reset of the current step is a loss, which the preview states with its absolute date (item 2). Modifiers
   are fixed and previewed, so there is no unpredictability. Result: pass.
2. **Full transparency.** The preview lists the exact modifiers, states that tasks start fresh and states that
   the current step resets to 0 at the season end (absolute date) before confirmation. `info`, sidebar and join line repeat them. No odds, no hidden costs. Result: pass.
3. **Bartle types.** Serves achievers (steps, highest step) and explorers (new constraints on a known
   system). Step 0 keeps the identical game, so the season is unchanged for players who do not opt in.
   Result: pass, with a note: no public prestige leaderboard in this release. A social comparison
   pressure would need its own review.
4. **Reward adapts to struggle or achievement?** Rewards do not adapt. The reward definition is the same
   at every step and is granted once per player, season, challenge and task (I4-10), at any step. A higher step pays
   only in recognition. Difficulty changes only on an explicit player
   choice, gated by completed tasks. Behavior signals are not collected or used. A "you seem to
   struggle, try a lower step" message would be an unsolicited nudge built from disengagement, and
   "suggestion only" use would need per-player session data that the data-minimization stance of
   existing tables rules out. The conservative option costs the player nothing, since `down` is always
   available. Result: pass, conservative option chosen.
5. **Late-joining or irregular player.** Step 0 is the full challenge, and prestige is purely additive
   and optional. Raising keeps lower-step completions, lowering keeps the highest step, and existing
   completions read as step 0. Season interplay is decided in D7. Result: pass.
6. **Skeptical observer one-liner.** "Players who finished a challenge can opt into documented harder
   versions and go back whenever they like." This is not describable as manipulative. The only
   candidate, the unlock line, is shown once per step and can be disabled per challenge. Result: pass.
7. **Falsifiable metrics** for `playtest-qa-coordinator`, from server logs and database aggregates, not
   per-player telemetry: (a) share of players who lower within their first round at a new step, failing
   above 50 percent at any step (the step is too steep); (b) step-0 task completion rate before and after
   release, failing if it drops (prestige talk pressures beginners); (c) qualitative playtest: every
   tester can state their current modifiers without opening a menu, failing otherwise (visibility is
   insufficient). Result: defined.
8. **Countdown.** None. Raising is blocked while a round runs. That is an organizational rule tied to the
   admin-controlled round, not a timer the player races. Result: pass.

Precedent for the modifier model: flat entries per step, bundled per step (Origins-style named bundle,
changeable by `up` and `down`). UHC scenarios are the closest precedent for an optional named modifier
on a base ruleset, here chosen per player rather than by host vote. Sources to cite in the design
review: FTC (2022) on dark patterns, Gray et al. (CHI 2018) on transparency and nagging, Csikszentmihalyi
on self-selected challenge, and prospect theory (why lowering must keep records).

## Test plan

All unit tests build their own fixtures. No shared static state, no sleeps, no system clock (inject
`java.time.Clock` where time is involved). Each test names one behavior. Tests are written before the
production code they cover.

- `api`: `PrestigeLadderTest` (contiguous steps, maximum 10, monotonic per modifier, strictly harder,
  empty ladder); one bounds test per modifier (for example `MaxHealth` rejects 1 and 21, accepts 2 and
  20); `PlayerPrestigeTest` (`current <= highest`); `ChallengeRuleTest` (old constructors default to an
  empty ladder, `withActive` keeps it).
- `core`, black-box on `PrestigeUseCase` and `TaskProgressUseCase`, with in-memory fakes of
  `PrestigeRepository` and `TaskCompletionRepository` in `testFixtures`: one test per `PrestigeChange`
  case; `raiseIsRejectedWhileChallengeActive`; `lowerIsAllowedWhileChallengeActive`;
  `raiseKeepsLowerStepCompletions`; `taskRewardGrantedOncePerTaskAtAnyStep`;
  `higherStepCompletionGrantsNoReward`; `reRaisingToVisitedStepDoesNotRegrantReward`; `unlockFiresOnceWhenLastTaskCompletes`;
  `activeModifiersServedFromCacheAfterFirstRead` (counting fake); `forgetEvictsCache`;
  `resetSeasonClearsCacheAndRepository`; `raiseRetriesOnceAfterConflict`, `secondConflictReturnsConcurrentChange`,
  `lowerRetriesOnceAfterConflict` (the fake returns `Conflict` on scripted calls).
- `adapters/config`: round trip per format (JSON, YAML, TOML) with `@TempDir`; an invalid ladder file
  loads its challenge with an empty ladder while the others are unaffected; `ConfigSchemasTest` asserts
  the `prestige` property and the modifier `type` enum appear in the challenge schema.
- `adapters/prestige` and the task progress persistence (Testcontainers `postgres:16-alpine`, one
  container per class, fresh random `UUID`s per test): find on an empty table is absent; save and
  upsert; check constraints reject `current > highest`; `resetSeason` clears the current steps only and bumps
  `version`; a completion row migrated from before the scope key reads as completed at step 0 and not at step 1;
  two `EntityManager`s load the same row and the second flush fails with `OptimisticLockException`, returned as
  `Conflict` (sequential, no threads, no sleeps); a duplicate first insert of one key returns `Conflict`.
  Extend `verification/PostgresAdapterMigrationTest` to migrate all adapters into one database.
- `adapters/command`: `PrestigeCommandsTest` against `RecordingCommandSource` and a stub
  `PrestigeUseCase`: which `Messages` factory fired per case; console guard; `up` without `confirm` never
  calls `raise`.
- `adapters/scoreboard` (MockBukkit): title suffix and summary line present at step above 0 and absent at
  step 0; the board is rebuilt on prestige change for that player only.
- `platforms/common` (MockBukkit, `server.getScheduler().performTicks(n)` instead of waiting): per
  behavior, the modifier applies only to the player at that step, only in a SkyGrid world, only while the
  challenge is active; max health is restored on step 0, deactivation, world change and quit; a denied
  station is tracked as a violation; `PrestigeFeature` sends the join line once; `ChallengeFeatureTest`
  disables a ladder with an unsupported modifier type, with the log asserted through a captured appender.
- ArchUnit (`verification`): no new rule. `apiPortsAndCoreAreFrameworkFree` and
  `adaptersDependOnPortsAndApiOnlyNotCore` cover the new types once `:adapters:prestige` is added to
  `verification/build.gradle.kts`. Optionally assert that `api.model` types implementing
  `DifficultyModifier` are records.
- Documentation: `prestige` is added to the adapter list in the `hexagonal-architecture` skill in the
  same group as the code that adds it.

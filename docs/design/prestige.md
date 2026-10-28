# I4 Adaptive meta-progression: opt-in prestige (requirements and design)

Status: specified, not implemented. Reference state: `origin/main`. Prerequisite: the Flyway fix
(one location and one history table per adapter, branch `origin/fix/flyway-migration-clash`) is
merged first. The season scope and the progress scope key come from `docs/design/seasons.md`.

Product owner decision this spec implements: **opt-in prestige with admin-configured bounds**.
The player raises (and lowers) their own prestige step per challenge; each step's difficulty is a
discrete, admin-configured, code-bounded set of modifiers; every adaptation is visible before and
while it applies; rewards are never coupled to struggle or disengagement signals. Behavior signals
(session-length trend, completion ratio, drop-off point, return rate) are **not used at all** in
this release (justified in section 3, item 4 and item 6).

Frame (I5 step 1): prestige lets a player who has demonstrably finished a challenge's goals choose a
harder, clearly described variant of the same challenge, encouraging mastery and replay through
voluntary self-set challenge rather than through pressure.

## 1. User stories and acceptance criteria

Proposed replacement text for `docs/USERSTORIES.md` I4 (the I5 gate stays as is). EARS patterns:
ubiquitous (`The system shall`), event-driven (`When`), state-driven (`While`), unwanted (`If ...
then`), optional feature (`Where`). MoSCoW tag per criterion.

### I4: Opt-in prestige per challenge

As a **player**, I want to choose a harder prestige step of a challenge I have already mastered, and
to see exactly what changes, so that the challenge stays interesting on replay without the game
deciding for me.

- I4-1 [Must] The system shall keep, per player, season and challenge, a current prestige step, where
  step 0 is the unmodified challenge and the default for every player, and shall keep the highest step
  ever reached as an all-time record.
- I4-2 [Must] When a player runs `/skygrid prestige up <challenge>`, the system shall show the next
  step's complete modifier list, the fact that this step's tasks start fresh, and a confirm action,
  and shall not change the step until the player confirms.
- I4-3 [Must] When a player confirms `/skygrid prestige up <challenge> confirm` and every configured
  task of that challenge is completed at the current step, the system shall raise the current step
  by exactly one and update the highest step if exceeded.
- I4-4 [Must] If the player has an incomplete task at the current step, then the system shall reject
  the raise and name the missing tasks.
- I4-5 [Must] If the current step is already the ladder's last configured step, then the system
  shall reject the raise and say that the maximum is reached.
- I4-6 [Must] While the challenge is active, the system shall reject a raise for that challenge and
  say the change is possible between rounds.
- I4-7 [Must] When a player runs `/skygrid prestige down <challenge>`, the system shall lower the
  current step by one at any time, including mid-round, without confirmation and without touching
  the highest step or any recorded task completion.
- I4-8 [Must] While a player's current step for an active challenge is above 0 and the player is in
  a SkyGrid world, the system shall apply exactly that step's configured modifiers to that player
  and to no other player.
- I4-9 [Must] The system shall record task completion per progress scope (season, player, challenge and prestige step) and task,
  so that a task completed at step N stays completed at step N after any raise or lower.
- I4-10 [Must] The system shall grant a task's configured reward at most once per player, challenge,
  task and prestige step, with the same reward definition at every step.
- I4-11 [Must] The system shall never derive a reward, its magnitude, or a modifier from session
  length, completion ratio, drop-off point, return rate, deaths, or any other struggle or
  disengagement signal.
- I4-12 [Must] While a player's current step for the first active challenge is above 0, the
  challenge sidebar shall show the step number and a one-line modifier summary.
- I4-13 [Must] When a player with a current step above 0 joins while that challenge is active, the
  system shall send one chat line naming the step and its modifiers (this also surfaces admin config
  changes to a step).
- I4-14 [Must] When a player runs `/skygrid prestige info [challenge]`, the system shall list current
  step, highest step, maximum step, the current modifiers, the next step's modifiers, and whether
  the raise is unlocked.
- I4-15 [Must] All player-facing prestige text shall be translatable and shipped in English and
  German.
- I4-16 [Should] When a player completes the last open task at their current step and a next step
  exists, the system shall send one neutral chat line that prestige is available, at most once per
  player, challenge and step, never repeated on later joins.
- I4-17 [Should] Where an admin sets `prestige.announceUnlock: false` for a challenge, the system
  shall not send the I4-16 line for that challenge.

As a **server admin**, I want to define the prestige steps per challenge in the existing challenge
files, within hard limits, so that I control how hard prestige gets and cannot ship an absurd step
by mistake.

- I4-20 [Must] The system shall read an optional `prestige` block from `challenges/<id>.<ext>` (JSON,
  YAML, TOML) and include it in the generated `schema/challenge.schema.json`.
- I4-21 [Must] If a ladder has non-contiguous step numbers, more than 10 steps, a value outside a
  modifier's hard bounds, a step that is not strictly harder than the previous one, or a modifier
  type the challenge's behavior does not support, then the system shall disable prestige for that
  challenge and log a warning naming the file and the offending step, without failing the load of
  other challenges.
- I4-22 [Must] When an admin runs `/skygrid prestige of <player> [challenge]`, the system shall show
  that player's steps.
- I4-23 [Must] When an admin runs `/skygrid prestige reset confirm`, the system shall reset every
  player's current step to 0 in the active season, keeping the all-time highest record, using the F2
  confirmation convention (`reset` alone only
  shows what will be lost).
- I4-24 [Could] Where a challenge has no `prestige` block, the system shall report it as "no
  prestige" in `info` and tab completion shall not offer it for `up`/`down`.
- I4-25 [Won't, this release] Behavior-signal based suggestions ("you might enjoy step 2"), automatic
  step changes, per-step reward overrides, and a cross-challenge global prestige.

## 2. Design

### 2.1 Difficulty modifiers per challenge

Step 0 is always the challenge exactly as it ships today. A step is a list of modifiers; each type
appears at most once per step. Hard bounds are validated in the records' compact constructors;
"monotonic" means each step must be at least as hard as the previous one for every modifier it
shares, and strictly harder in at least one (validated in `PrestigeLadder`). A modifier absent from
a step inherits nothing: every step lists its full set, so the config reads the same as the preview.

| Challenge | Modifier `type` | Field | Effect at step > 0 | Hard bounds | Monotonic rule |
|---|---|---|---|---|---|
| `one_life` | `max_health` | `halfHearts` | Sets the player's max health (attribute modifier, removed on step 0/inactive/world change) | 2..20 | non-increasing |
| `one_life` | `damage_taken_multiplier` | `multiplier` | Multiplies final damage the player takes | 1.0..3.0 | non-decreasing |
| `no_natural_regeneration` | `blocked_regain_sources` | `sources` | Cancels these regain sources in addition to the base `satiated` | subset of `satiated`, `regen_effect`, `instant_health` | superset of previous |
| `no_natural_regeneration` | `exhaustion_multiplier` | `multiplier` | Multiplies hunger exhaustion | 1.0..3.0 | non-decreasing |
| `no_crafting_table` | `denied_stations` | `blocks` | Denies opening these blocks in addition to `minecraft:crafting_table` (same round-violation tracking) | 1..16 namespaced `BlockId`s | superset of previous |
| `level_is_border` | `blocks_per_level` | `perLevel` | Lowers the displayed radius factor (today hardcoded 10) for that player's action bar | 1..10 | non-increasing |

Notes:
- Regain sources are a platform-neutral `api.model.RegainSource` enum; `platforms.olf.challenge`
  maps them to Bukkit `RegainReason` (`SATIATED`, `REGEN`/`MAGIC_REGEN`, `MAGIC`). Stations are
  `BlockId`, resolved to `Material` only in the platform (golden rule: no `Material` names in config).
- `level_is_border` is display-only today; a "harder" number that nothing enforces is fake
  difficulty. Its bundled default therefore ships **without** a `prestige` block; the modifier type
  exists so an admin or a later enforcement story can use it. `no_natural_regeneration`'s task is
  attendance-based (I2 skeleton), so its ladder is unlocked by attendance; acceptable because
  prestige only adds difficulty, never reward magnitude.
- Bundled defaults (adjustable): `one_life` 3 steps (16 / 12 / 12 half-hearts, multipliers 1.0 /
  1.0 / 1.25); `no_natural_regeneration` 2 steps (`regen_effect`; then plus exhaustion 1.5);
  `no_crafting_table` 2 steps (`furnace`, `smithing_table`; then plus `anvil`, `enchanting_table`).

### 2.2 Domain model (`api.model`, records, validated in compact constructors)

- `sealed interface DifficultyModifier` with records `MaxHealth(int halfHearts)`,
  `DamageTakenMultiplier(double multiplier)`, `BlockedRegainSources(Set<RegainSource> sources)`,
  `ExhaustionMultiplier(double multiplier)`, `DeniedStations(Set<BlockId> blocks)`,
  `BlocksPerLevel(int perLevel)`. Each has `static` bound constants and a `boolean
  atLeastAsHardAs(DifficultyModifier previous)`; consumers use an exhaustive `switch` without
  `default`.
- `enum RegainSource { SATIATED, REGEN_EFFECT, INSTANT_HEALTH }`.
- `record PrestigeStep(int step, List<DifficultyModifier> modifiers)`: `step >= 1`, non-empty,
  unique modifier types, `List.copyOf`.
- `record PrestigeLadder(List<PrestigeStep> steps, boolean announceUnlock)`: `MAX_STEPS = 10`, steps
  numbered 1..n contiguous, monotonic and strictly harder per step; `static empty()`,
  `Optional<PrestigeStep> step(int)`, `int maxStep()`.
- `ChallengeRule` gains a sixth component `PrestigeLadder prestige` (null to `empty()`); the existing
  3- and 5-argument constructors keep compiling and default to `empty()`, `withActive` carries it.
- `record PlayerPrestige(SeasonId season, UUID playerId, String challengeId, int currentStep, int highestStep)`:
  `0 <= currentStep <= highestStep <= PrestigeLadder.MAX_STEPS`, challenge id rules as
  `ChallengeTier`. `static initial(UUID, String)`.

### 2.3 Ports

- `ports.out.PrestigeRepository`: `Optional<PlayerPrestige> find(SeasonId season, UUID playerId, String challengeId)`,
  `void save(PlayerPrestige prestige)` (upsert), `void resetSeason(SeasonId season)`,
  `int allTimeHighest(UUID playerId, String challengeId)` (maximum `highestStep` over all seasons).
- `ports.out.TaskCompletionRepository` (consumed, not changed here): `isCompleted` and `markCompleted` take the `ProgressScope` of the
  seasons spec, whose `prestigeStep` is this spec's step. Only caller is `TaskProgressService`.
- `ports.in.PrestigeUseCase`:
  - `PrestigeStatus statusOf(UUID, String challengeId)`; `record PrestigeStatus(PlayerPrestige
    prestige, PrestigeLadder ladder, List<DifficultyModifier> current, Optional<PrestigeStep> next,
    List<Task> missingTasks)`.
  - `PrestigeChange raise(UUID, String)` and `PrestigeChange lower(UUID, String)`; `sealed interface
    PrestigeChange` with `Raised(PrestigeStatus)`, `Lowered(PrestigeStatus)`, `TasksMissing(List<Task>)`,
    `AtMaximum`, `AtMinimum`, `ChallengeRunning`, `NoLadder`, `UnknownChallenge`.
  - `List<DifficultyModifier> activeModifiers(UUID, String)` (hot path, cached).
  - `void forget(UUID playerId)` (cache eviction on quit), `void resetCurrentSeason()`,
    `void addChangeListener(PrestigeChangeListener)` (`(UUID, String challengeId, int newStep)`).
- `ports.in.TaskProgressUseCase`: unchanged signatures; `tasksFor`/`completeTask` resolve the
  player's current step internally.

### 2.4 Core services (`core`)

- `core.state.PrestigeLevels`: owns `PrestigeRepository` reads and a `ConcurrentHashMap` cache of
  `PlayerPrestige` per (player, challenge), cleared on season switch; `current(UUID, String)`, `put`, `forget`, `clear`. Shared
  by both services below, which breaks the cycle (prestige eligibility needs task status, task
  status needs the current step).
- `core.service.PrestigeService implements PrestigeUseCase`: depends on `ChallengeState`,
  `PrestigeLevels`, `PrestigeRepository`, `TaskCompletionRepository`. `raise` order of checks:
  unknown challenge, no ladder, at maximum, challenge active (I4-6), missing tasks at current step,
  then save and fire listeners. `lower` checks only unknown/at minimum. No method reads a clock, a
  session, a death count or any behavior metric (I4-11 enforced by construction: the port has no
  such input).
- `core.service.TaskProgressService` (changed): takes `PrestigeLevels`, passes the current step to
  the repository. Raising is blocked while the challenge runs and lowering is allowed, so the step is
  non-increasing during a round; round-end credit at the current step can never be inflated by a
  late raise (closes the "play easy, raise before the round ends" exploit).
- Unlock announcement (I4-16): `PrestigeService` registers a `TaskCompletionListener`; when the
  completed task closes the current step, it fires `PrestigeChangeListener`-style `onUnlocked`.
  "Once per step" is stored implicitly: it fires only on the completion event itself, which is
  idempotent per step, so no extra column is needed.

### 2.5 Adapters

Config (`adapters/config`):
- `ChallengeDtos.ChallengeDto` gains `PrestigeDto prestige`; `PrestigeDto(List<PrestigeStepDto>
  steps, Boolean announceUnlock)`, `PrestigeStepDto(Integer step, List<ModifierDto> modifiers)`,
  `ModifierDto(String type, Integer halfHearts, Double multiplier, List<String> sources,
  List<String> blocks, Integer perLevel)`, a flat discriminated DTO like `RewardDto`. The
  `blocks_per_level` integer uses field `perLevel` to avoid clashing with `denied_stations.blocks`.
- `JacksonChallengeRepository` maps DTO to model; a record-constructor `IllegalArgumentException`
  for one ladder is caught per file, logged with file and step, and the rule is loaded with
  `PrestigeLadder.empty()` (I4-21). Schema regenerates from the DTOs via `ConfigSchemas`, so
  editors get completion for `type` and fields; add `@JsonPropertyDescription` with the bounds.
- Example (`challenges/one_life.yml`):

```yaml
prestige:
  announceUnlock: true
  steps:
    - step: 1
      modifiers:
        - { type: max_health, halfHearts: 16 }
    - step: 2
      modifiers:
        - { type: max_health, halfHearts: 12 }
    - step: 3
      modifiers:
        - { type: max_health, halfHearts: 12 }
        - { type: damage_taken_multiplier, multiplier: 1.25 }
```

Persistence (`adapters/prestige`, new module, package `adapters.prestige`):
- `PostgresPrestigeRepository extends AbstractJpaRepository` with
  `super("prestige", "classpath:db/migration/prestige", "flyway_history_prestige", configSupplier)`,
  `PlayerPrestigeEntity` + `PlayerPrestigeId`, own `META-INF/persistence.xml`
  (`hbm2ddl.auto=validate`), upsert in `inTransaction`, Criteria delete for `resetSeason`.
- `db/migration/prestige/V1__create_player_prestige.sql`:

```sql
-- Per-player, per-challenge prestige step within one season. Row absence means step 0. Only the
-- counters are stored (no timestamps, no history, no behavior data), per the data minimization
-- stance of challenge_tier and task_completion.
CREATE TABLE player_prestige (
    season_id    TEXT NOT NULL CHECK (char_length(season_id) <= 64),
    player_id    UUID NOT NULL,
    challenge_id TEXT NOT NULL CHECK (char_length(challenge_id) <= 64),
    current_step SMALLINT NOT NULL CHECK (current_step >= 0 AND current_step <= 10),
    highest_step SMALLINT NOT NULL CHECK (highest_step >= current_step AND highest_step <= 10),
    PRIMARY KEY (season_id, player_id, challenge_id)
);
```

The all-time highest step is the maximum `highest_step` over all seasons of a player and challenge.
Season rows are archived with their season, never deleted. Task completions need no migration in this
spec: the seasons spec's `task_completion` migration already adds `season_id` and `prestige_step`
(step 0 for every existing row), so this slice only consumes the `ProgressScope` it defines.

Commands (`adapters/command`): new `PrestigeCommands implements CloudCommandHandler`, bound in
`CommandModule`'s set binder, same shape as `ChallengeCommands` (console guard via
`source.playerId()`, `Messages.*` factories, `@Argument(suggestions = "prestige-challenge-ids")`,
a new suggestion provider in `CloudCommandRegistrar` listing challenges with a non-empty ladder):

| Command | Permission | Result |
|---|---|---|
| `skygrid prestige info [challenge]` | `skygridng.prestige` (default true) | I4-14; without argument, one line per challenge with a ladder |
| `skygrid prestige up <challenge>` | `skygridng.prestige` | preview + click-to-confirm (I4-2) |
| `skygrid prestige up <challenge> confirm` | `skygridng.prestige` | `raise`, one message per `PrestigeChange` case |
| `skygrid prestige down <challenge>` | `skygridng.prestige` | `lower` (I4-7) |
| `skygrid prestige of <player> [challenge]` | `skygridng.admin` | I4-22 |
| `skygrid prestige reset` / `reset confirm` | `skygridng.admin` | I4-23 |

`info` is a literal rather than an optional argument on `prestige` itself, to avoid literal/variable
sibling ambiguity in Cloud. `HelpCommands` and `skygridng.command.help.*` get a prestige line;
`plugin.yml` declares `skygridng.prestige` with `default: true`.

Scoreboard (`adapters/scoreboard`): `ChallengeSidebarListener` takes `PrestigeUseCase`; the title
gets a ` [P<n>]` suffix and the top line is a translatable modifier summary (I4-12) when the step is
above 0; it subscribes to `addChangeListener` and rebuilds that one player's board in place
(prestige changes are rare, so a rebuild is fine; task updates keep the existing in-place path).

### 2.6 Platform wiring (`platforms/common`)

- `RepositoryModule`: `@Provides @Singleton PrestigeRepository` returning
  `new PostgresPrestigeRepository(DatabaseConfig::fromEnvironment)`; `PrestigeLevels`,
  `PrestigeService` (bound to `PrestigeUseCase`), and the changed `TaskProgressService` provider.
- `ChallengeBehavior` gains `default Set<Class<? extends DifficultyModifier>> supportedModifiers()`
  returning `Set.of()`; each behavior overrides it. `ChallengeFeature.enable()` checks every ladder
  against its behavior and disables (with warning) any ladder using an unsupported type (I4-21).
- Behaviors in `platforms.olf.challenge` take `PrestigeUseCase` and read
  `activeModifiers(player, CHALLENGE_ID)` only after their existing `isActive`/`appliesTo` guards:
  `OneLifeListener` adds an `EntityDamageEvent` handler (multiplier) and applies/removes a keyed
  `AttributeModifier` on `MAX_HEALTH` on join, world change, catalog change and prestige change;
  `NoNaturalRegenerationListener` widens its reason check and adds an `EntityExhaustionEvent`
  handler; `NoCraftingTableListener` checks a per-player `Set<Material>` resolved from `BlockId`
  (unknown ids dropped with a warning, like `ItemResolver`); `LevelBorderWarningTask` computes the
  radius per recipient.
- New `PrestigeFeature` (`OlfFeature`, bound in `OlfModule`): a listener that sends the I4-13 join
  line, calls `forget` on quit, and relays unlock/raise/lower notices (I4-16).
- i18n (`messages/Bundle_en.properties`, `Bundle_de.properties`, Crowdin picks up `en`):
  `skygridng.command.prestige.{info.header,info.line,info.none,preview,preview.fresh_tasks,
  confirm_hint,raised,lowered,tasks_missing,at_maximum,at_minimum,challenge_running,no_ladder,
  console,of,reset,reset.confirm}`, `skygridng.prestige.{join_summary,unlocked,sidebar_suffix}`,
  and one `skygridng.prestige.modifier.<type>` key per modifier type (e.g. `max_health=<arg:0>
  hearts max` / `<arg:0> Herzen maximal`). The summary joins modifier components, so every modifier
  is described the same way in preview, info, join line and sidebar.

## 3. I5 dark-pattern review (checklist from `challenge-season-system`)

1. **Octalysis drives**: development/accomplishment (visible step, highest step), empowerment and
   ownership (player chooses, can lower). Loss/avoidance exists only inside challenges that already
   have it (`one_life` elimination), prestige does not add a new loss: lowering is free and keeps
   every record. No unpredictability: modifiers are fixed and previewed. **Pass.**
2. **Full transparency**: preview lists the exact modifiers and that tasks start fresh before
   confirm; `info`, sidebar and join line repeat them; there are no odds or hidden costs. The
   mechanic works because players want a harder game, not because they misjudge it. **Pass.**
3. **Bartle types**: serves achievers (steps, highest step) and explorers (new constraints on known
   systems). Socializers and killers are neither served nor harmed; step-0 players keep the
   identical game, so the season as a whole is unchanged for them. **Pass, with a note**: no
   public prestige leaderboard in this release, which would add a social-comparison pressure that
   needs its own review.
4. **Reward adapts to struggle or to achievement?** Rewards do not adapt at all: same task reward
   definition at every step, granted once per step (I4-10). Difficulty adapts only to an explicit
   player choice gated by demonstrated achievement (all tasks done). Behavior signals are unused:
   even "suggestion only" use would require collecting per-player session and return data (against
   the data-minimization stance of every existing table) and a message like "you seem to struggle,
   try a lower step" is an unsolicited nudge derived from disengagement. The conservative option
   costs nothing the player cannot do themselves with `down`. **Pass (conservative option chosen).**
5. **Late-joining / irregular player**: step 0 is the full challenge; prestige is purely additive
   and optional. Nothing is taken away: raising keeps lower-step completions, lowering keeps the
   highest step, the seasons migration assigns existing completions to step 0. Season interplay is decided
   section 6, decision 1. **Pass.**
6. **Skeptical observer one-liner**: "Players who finished a challenge can opt into documented
   harder versions and go back whenever they like." Not describable as manipulative. The only
   candidate, the unlock line, is shown once per step and admin-disableable. **Pass.**
7. **Falsifiable metrics** for `playtest-qa-coordinator` (from server logs/DB aggregates, not
   per-player telemetry): (a) share of players who lower within their first round at a new step
   (failing if above 50 percent at any step: that step is too steep); (b) step-0 task completion
   rate before and after release (failing if it drops, meaning prestige talk pressures beginners);
   (c) qualitative playtest: every tester can state their current modifiers without opening a
   menu (failing if not: visibility is insufficient). **Defined.**
8. **Countdown**: none. Raising is blocked while a round runs, which is an organizational rule tied
   to the admin-controlled round, not a timer the player races. **Pass.**

Step 3 precedent check: modifiers are Vanilla Tweaks-style flat entries per step but bundled per
step (Origins-style named bundle, changeable later by `up`/`down`); UHC scenarios are the closest
precedent for "optional named modifier on a base ruleset", here selected per player rather than by
host vote (own inference). Hypixel Bingo's fresh profile informs decision 1. Sources to
cite: FTC 2022, Gray et al. CHI 2018 (transparency and nagging checks),
Csikszentmihalyi flow (self-selected challenge), prospect theory (why lowering must keep records).

## 4. Test plan (F.I.R.S.T.)

All unit tests build their own fixtures, no shared static state, no sleeps, no system clock.

- `api` (white-box): `PrestigeLadderTest` (contiguous steps, max 10, monotonic per modifier, strictly
  harder, empty ladder), one test per modifier bound (`MaxHealthTest` rejects 1 and 21, accepts 2 and
  20, etc.), `PlayerPrestigeTest` (`current <= highest`), `ChallengeRuleTest` (old constructors
  default to empty ladder, `withActive` keeps it).
- `core` (black-box on `PrestigeUseCase`/`TaskProgressUseCase`, in-memory `PrestigeRepository` and
  `TaskCompletionRepository` fakes in `testFixtures`): one test per `PrestigeChange` case;
  `raiseIsRejectedWhileChallengeActive`; `lowerIsAllowedWhileChallengeActive`;
  `raiseKeepsLowerStepCompletions`; `taskRewardGrantedOncePerStep`; `reRaisingToVisitedStepDoesNotRegrantReward`;
  `unlockFiresOnceWhenLastTaskCompletes`; `activeModifiersServedFromCacheAfterFirstRead` (counting
  fake); `forgetEvictsCache`; `resetSeasonClearsCacheAndRepository`.
- `adapters/config`: round-trip per format with `@TempDir`; invalid ladder file loads the challenge
  with an empty ladder and the others unaffected; `ConfigSchemasTest` asserts the `prestige`
  property and modifier `type` enum appear in the challenge schema.
- `adapters/prestige` and `adapters/task-progress` (Testcontainers `postgres:16-alpine`, one
  container per class, fresh random `UUID`s per test so tests are independent): find on empty is
  absent, save/upsert, check constraints reject `current > highest`, `resetSeason`; task-progress:
  a row migrated from before the scope key reads as completed at step 0 and not at step 1. Extend
  `verification/PostgresAdapterMigrationTest` to migrate all three adapters into one database.
- `adapters/command`: `PrestigeCommandsTest` against `RecordingCommandSource` and a stub
  `PrestigeUseCase`; asserts which `Messages.*` fired per case, console guard, `up` without
  `confirm` never calls `raise`.
- `adapters/scoreboard` (MockBukkit): title suffix and summary line present at step above 0, absent
  at 0; board rebuilt on prestige change for that player only.
- `platforms/common` (MockBukkit, `server.getScheduler().performTicks(n)` instead of waiting): per
  behavior, modifier applied only to the player at that step, only in a SkyGrid world, only while
  active; max health restored on step 0, deactivation and world change; denied station tracked as a
  violation; `PrestigeFeature` sends the join line once; `ChallengeFeatureTest` disables a ladder
  with an unsupported modifier type (log asserted via a captured appender).
- ArchUnit: no new rule is needed; `apiPortsAndCoreAreFrameworkFree`, `adaptersDependOnPortsAndApiOnlyNotCore`
  and the layered rule cover the new types once `:adapters:prestige` is added to
  `verification/build.gradle.kts` (otherwise ArchUnit never imports it). Add
  `prestige` to the per-adapter documentation in `hexagonal-architecture`; optionally a rule that
  `api.model` classes implementing `DifficultyModifier` are records.

## 5. Delivery

The delivery plan for seasons, prestige and economy is one ordered list in
[`docs/design/README.md`](README.md#delivery-plan). Slice titles and order are defined there.

## 6. Decisions

The product owner resolved the open questions of this spec as follows.

1. **Prestige per season.** The current step resets with the season, so every player starts a new
   season at step 0. The highest step ever reached is kept as an all-time record and shown in
   `info`. The season scope and the progress scope key are defined in `docs/design/seasons.md`
   (decision 7).
2. **Rewards for higher steps.** Higher steps never pay more. There are no currency or per-step
   reward overrides in this release (I4-25).
3. **`level_is_border`.** It ships with no default prestige ladder until its border is enforced. The
   modifier type exists so a later enforcement story can use it.
4. **Unlock gate.** The gate stays "all tasks at the current step". Prestige adds no reward magnitude,
   so a weak gate cannot be farmed. It is revisited when `no_natural_regeneration` gets an
   achievement-based task.

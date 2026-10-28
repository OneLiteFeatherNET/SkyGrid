# Tasks

Each group follows the delivery slices 10 to 16 of `docs/design/README.md`. In every group the test
task comes first (red), then the production task that makes it pass (green). Each group lands its own
tests and documentation. Run only the touched modules' tests with `--max-workers=2`.

## 1. Domain model (delivery slice 10, `feat(api)`)

- [ ] 1.1 Write `PrestigeLadderTest` and the bounds tests for each `DifficultyModifier` record (failing first), covering contiguous steps, maximum 10, monotonic per modifier, strictly harder, empty ladder, and rejected values 1 and 21 for `MaxHealth`; verify the tests fail for the right reason
- [ ] 1.2 Write `PlayerPrestigeTest` and `ChallengeRuleTest` (old constructors default to an empty ladder, `withActive` keeps it) (failing first); verify they fail before production code exists
- [ ] 1.3 Implement `DifficultyModifier`, `RegainSource`, `PrestigeStep`, `PrestigeLadder`, `PlayerPrestige` and the sixth `ChallengeRule` component in `api.model` with compact-constructor validation; verify `./gradlew :api:test --max-workers=2` passes
- [ ] 1.4 Add the `api.model` section for prestige to the `hexagonal-architecture` skill and verify `compile-core.sh` still passes

## 2. Challenge ladder configuration (delivery slice 11, `feat(config)`)

- [ ] 2.1 Write the `adapters/config` round-trip tests per format (JSON, YAML, TOML) with `@TempDir`, the invalid-ladder isolation test (challenge loads with empty ladder, others unaffected) and the `ConfigSchemasTest` assertions for the `prestige` property and modifier `type` enum (failing first); verify they fail
- [ ] 2.2 Add `PrestigeDto`, `PrestigeStepDto`, `ModifierDto` to `ChallengeDtos`, map them in `JacksonChallengeRepository`, catch the per-ladder `IllegalArgumentException` with a warning naming file and step, and add `@JsonPropertyDescription` bounds; verify `./gradlew :adapters:config:test --max-workers=2` passes
- [ ] 2.3 Ship the bundled `prestige` defaults for `one_life` and `no_natural_regeneration` and `no_crafting_table` in their challenge files and document the block in `config-schema-and-rules`; verify the existing config tests still pass and `level_is_border` has no ladder

## 3. Player prestige persistence (delivery slice 12, `feat(persistence)`)

- [ ] 3.1 Write the Testcontainers tests for `PostgresPrestigeRepository` (`postgres:16-alpine`, fresh random `UUID`s per test): find on empty is absent, save and upsert, check constraints reject `current > highest`, `resetSeason` clears current steps only and bumps `version`, `allTimeHighest` over seasons, two `EntityManager`s loading the same row throw `PrestigeConflictException` on the second flush (sequential, no threads, no sleeps), and a duplicate first insert of one key throws `PrestigeConflictException` (failing first); verify they fail
- [ ] 3.2 Add package `adapters.prestige` to the existing `adapters/persistence` module with `PostgresPrestigeRepository`, `PlayerPrestigeEntity` keyed by the `@EmbeddedId` record `PlayerPrestigeId`, `@Version`, the shared `SeasonIdConverter`, the entity-to-record mapper, the unchecked `PrestigeConflictException` in `ports.out` (thrown by the repository on a lost race), `META-INF/persistence.xml` (`hibernate.hbm2ddl.auto=validate`), the `db/migration/prestige/V1__create_player_prestige.sql` migration (with `version`) and the `flyway_history_prestige` history table; verify `./gradlew :adapters:persistence:test --max-workers=2` passes
- [ ] 3.3 Extend `verification/PostgresAdapterMigrationTest` to migrate the prestige adapter into the shared database next to the others; no Gradle file changes are needed because the package lives in `adapters/persistence`; verify the migration test passes
- [ ] 3.4 Write the migration test that a completion row from before the scope key reads as completed at step 0 and not at step 1 (failing first), and verify it passes against the merged `add-season-lifecycle` migration

## 4. Prestige use case (delivery slice 13, `feat(core)`)

- [ ] 4.1 Write the `PrestigeUseCase` tests in `core` with in-memory fakes in `testFixtures` (failing first): one test per `PrestigeChange` case, `raiseIsRejectedWhileChallengeActive`, `lowerIsAllowedWhileChallengeActive`, `raiseKeepsLowerStepCompletions`, `lowerKeepsHighestStep`, `forgetEvictsCache`, `resetSeasonClearsCacheAndRepository`, and `activeModifiersServedFromCacheAfterFirstRead` with a counting fake
- [ ] 4.2 Write the concurrency tests in `core` with the fake scripted to throw `PrestigeConflictException` (failing first): `raiseRetriesOnceAfterConflict` (the second attempt re-reads the row and succeeds), `secondConflictReturnsConcurrentChange` (no write, the cache is dropped), and `lowerRetriesOnceAfterConflict`
- [ ] 4.3 Write the reward and unlock tests (failing first): `taskRewardGrantedOncePerStep`, `reRaisingToVisitedStepDoesNotRegrantReward`, `unlockFiresOnceWhenLastTaskCompletes`
- [ ] 4.4 Implement `ports.in.PrestigeUseCase`, `ports.out.PrestigeRepository` (`save` returns void and throws `PrestigeConflictException`), `PrestigeChange` (with `ConcurrentChange`), `PrestigeStatus`, `core.state.PrestigeLevels` and `core.service.PrestigeService` with the check order and the one-retry rule from the design; change `TaskProgressService` to pass the current step into the completion repository; verify `./gradlew :core:test --max-workers=2` passes
- [ ] 4.5 Add a `verification` ArchUnit assertion that `api.model` types implementing `DifficultyModifier` are records, and verify `./gradlew :verification:test --max-workers=2` passes

## 5. Prestige commands (delivery slice 14, `feat(command)`)

- [ ] 5.1 Write `PrestigeCommandsTest` against `RecordingCommandSource` and a stub `PrestigeUseCase` (failing first): which `Messages` factory fires per `PrestigeChange` case (including `ConcurrentChange`), the console guard, `up` without `confirm` never calls `raise`, `reset` alone never resets, `info` without argument lists only challenges with a ladder
- [ ] 5.2 Implement `PrestigeCommands implements CloudCommandHandler`, the `Messages` factories, the `prestige-challenge-ids` suggestion provider in `CloudCommandRegistrar` and the set-binder entry in `CommandModule`; verify `./gradlew :adapters:command:test --max-workers=2` passes
- [ ] 5.3 Add the `skygridng.prestige.*` and `skygridng.command.prestige.*` keys to `Bundle_en.properties` and `Bundle_de.properties`, add the help line in `HelpCommands`, declare the `skygridng.prestige` permission (default true) in `plugin.yml`, and document the commands in the user documentation; verify a missing German key fails the bundle test
- [ ] 5.4 Write a red `PrestigeCommandsTest` case that the `up` preview without `confirm` names the season end as an absolute date and says the current step resets to 0 there, and that it changes no step; verify it fails
- [ ] 5.5 Add the `Messages` key that renders the season end date in the viewer locale and make the 5.4 test pass

## 6. Challenge behaviors apply modifiers (delivery slice 15, `feat(challenge)`)

- [ ] 6.1 Write the MockBukkit tests for `OneLifeListener` (failing first): the damage multiplier and max health apply only to the player at that step, only in a SkyGrid world, only while the challenge is active; max health is restored on step 0, deactivation, world change and quit
- [ ] 6.2 Write the MockBukkit tests for `NoNaturalRegenerationListener` and `NoCraftingTableListener` (failing first): extra regain sources and exhaustion multiplier per step; a denied station counts as a round violation; unknown `BlockId`s are dropped with a warning
- [ ] 6.3 Write the `LevelBorderWarningTask` test (failing first) that the radius is computed per recipient and that `blocks_per_level` is only read for a player with a step, using `server.getScheduler().performTicks(n)`
- [ ] 6.4 Implement the modifier handling in the four behaviors in `platforms.olf.challenge`, add `supportedModifiers()` to `ChallengeBehavior`, and disable unsupported ladders in `ChallengeFeature.enable()`; write the `ChallengeFeatureTest` case that asserts the warning through a captured appender first; verify `./gradlew :platforms:common:test --max-workers=2` passes
- [ ] 6.5 Implement `PrestigeFeature` (`OlfFeature` bound in `OlfModule`): the once-per-join line (test first), `forget` on quit and unlock and raise and lower notices, with the unlock line once per step and disabled by `announceUnlock: false`

## 7. Sidebar and visibility (delivery slice 16, `feat(scoreboard)`)

- [ ] 7.1 Write the MockBukkit scoreboard tests (failing first): title suffix ` [P<n>]` and the modifier summary line present at step above 0, absent at step 0, board rebuilt on a prestige change for that player only
- [ ] 7.2 Implement the `ChallengeSidebarListener` change that takes `PrestigeUseCase`, subscribes to `addChangeListener` and rebuilds only that player's board (the class lives in `platforms/common`, package `platforms.olf.scoreboard`); verify `./gradlew :platforms:common:test --max-workers=2` passes
- [ ] 7.3 Document the prestige step and the `info` output for players in the user documentation and verify the documented command output matches the message keys

## 8. Integration checks

- [ ] 8.1 Run `./gradlew build` for the touched modules with `--max-workers=2` and verify it is green, including ArchUnit and the migration test
- [ ] 8.2 Start the test server (`./gradlew :platforms:paper:runServer`), perform one raise, one lower and one invalid ladder load by hand, and verify the sidebar, join line and `info` output match the design
- [ ] 8.3 Open the pull request titled `feat(prestige): let players opt into bounded prestige steps`, with the description filled from the repository PR template and no session attribution

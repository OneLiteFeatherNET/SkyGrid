# Tasks

Groups 1 to 8 follow delivery slices 2 to 9 of `docs/design/README.md`. Within each group, every
test task comes before the production task it covers (red, then green). Run only the touched
modules' tests with `--max-workers=2`, for example `./gradlew :core:test --max-workers=2`.

## 1. API model (delivery slice 2)

- [ ] 1.1 Write red record tests in `api` for `SeasonId` (pattern `[a-z0-9_]{1,64}`, rejects empty and 65 characters), `Season` (positive duration, `end()` equals start plus duration, `contains` is start inclusive and end exclusive) and `ProgressScope` (prestige step 0 and 10 accepted, 11 and -1 rejected); verify `./gradlew :api:test --max-workers=2` fails for the missing types
- [ ] 1.2 Implement `SeasonId`, `Season`, `SeasonCatalog`, `SeasonPhase`, `SeasonRun`, `SeasonStatus` and `ProgressScope` as immutable records with compact-constructor validation; verify the tests from 1.1 pass
- [ ] 1.3 Write a red test that `SkyGridApi#currentSeason()` returns `Optional.empty()` when no season is configured; verify it fails
- [ ] 1.4 Add `SkyGridApi#currentSeason()` as a read-only facade method; verify the test from 1.3 passes and `./gradlew :verification:test --max-workers=2` stays green

## 2. Season configuration (delivery slice 3)

- [ ] 2.1 Write red `JacksonSeasonRepositoryTest` with `@TempDir`: JSON, YAML and TOML round trip, file id is authoritative, `.disabled` example is not loaded, empty `seasons/` stays empty; verify the test fails
- [ ] 2.2 Write red tests in `core` for season validation: unknown preset rejected with file, value and known values in the message; unknown challenge rejected; overlapping seasons reject the later one naming both ids; other seasons keep loading; verify the tests fail
- [ ] 2.3 Implement `SeasonDtos`, `JacksonSeasonRepository` and the `SeasonRepository` port, with `SeasonTimeline` validation for presets, challenges and overlap; verify the tests from 2.1 and 2.2 pass
- [ ] 2.4 Write a red test that `ConfigSchemas.Kind.SEASON` writes `schema/season.schema.json` next to the other schemas; verify it fails, then implement it and verify it passes
- [ ] 2.5 Write a red test that an absent or empty `seasons/` directory yields one implicit `legacy` season with no scheduled transition; verify it fails, then implement the fallback and verify it passes
- [ ] 2.6 Write a red test for the optional `season` block in `config` (`prepareLead` default `PT15M`, `archive.directory`, `archive.compress` default false); verify it fails, then implement the parsing and verify it passes
- [ ] 2.7 Write a red test that `season reload` without `confirm` does not re-read `seasons/`, and that a confirmed reload changes only future seasons; verify it fails, then implement the reload use case path and verify it passes

## 3. Scoped progress persistence (delivery slice 4)

- [ ] 3.1 Write red `PostgresChallengeProgressRepositoryTest` (Testcontainers `postgres:16-alpine`, fresh table per test): season isolation, `resetSeason` leaves other seasons unchanged, history query returns one tier per season; verify it fails
- [ ] 3.2 Write red `PostgresTaskCompletionRepositoryTest`: completions are keyed by season, prestige step and task id, `resetSeason` leaves other seasons, `completedCountBySeason` returns counts per season; verify it fails
- [ ] 3.3 Write a red migration test in `verification` (extending `PostgresAdapterMigrationTest` from the Flyway fix) that a database with pre-season rows migrates, and every row lands in season `legacy` with step 0 and unchanged row count; verify it fails
- [ ] 3.4 Add `db/migration/challenge-progress/V3__scope_tier_by_season.sql` and the season-scoped `ChallengeProgressRepository` methods (`tierOf`, `recordCompletion`, `resetSeason`, `history`); verify the tests from 3.1 and 3.3 for the challenge table pass
- [ ] 3.5 Add `db/migration/task-progress/V2__scope_completion_by_season_and_step.sql` and the `TaskCompletionRepository` changes keyed by `ProgressScope` plus `taskId`; verify the tests from 3.2 and 3.3 for the task table pass
- [ ] 3.6 Write red `ChallengeProgressServiceTest` and `TaskProgressServiceTest` asserting that writes use `CurrentSeason#id()`; verify they fail, then rename `resetAll` to `resetCurrentSeason`, pass the season id through the services, and verify they pass
- [ ] 3.7 Write red `ArchUnit` coverage that the progress adapters stay framework free where they were before; verify it passes on the current code, then confirm `./gradlew :verification:test --max-workers=2` is green after 3.4 and 3.5

## 4. Season run repository (delivery slice 5)

- [ ] 4.1 Write red `PostgresSeasonRunRepositoryTest` (Testcontainers, fresh table per test): save and find, `active()` returns the single active run, `inFlight()` excludes `ACTIVE` and `ARCHIVED`; verify it fails
- [ ] 4.2 Write red test that `switchActive(from, to, at)` is atomic: the old season becomes `CLOSING` with `ended_at`, the new season becomes `ACTIVE` with `started_at`, and a failure in between leaves both unchanged; verify it fails
- [ ] 4.3 Write red test that the partial unique index rejects a second `ACTIVE` row; verify it fails
- [ ] 4.4 Add `db/migration/season/V1__create_season_run.sql`, `PostgresSeasonRunRepository` (persistence unit `season`, history table `flyway_history_season`, `hbm2ddl=validate`) and `SeasonRunEntity`; verify the tests from 4.1 to 4.3 pass
- [ ] 4.5 Write red ArchUnit rule `seasonPersistenceAdapterIsFrameworkFree` (no `org.bukkit`, Guice or jakarta.inject in `adapters.season` apart from the JPA base) and a rule that `core` does not call `Clock.systemUTC()`, `Instant.now()` or `System.currentTimeMillis()`; verify they fail, then add the rules and the `adapters.season` package to `ArchitectureTest` and verify they pass

## 5. Timeline and transition state machine (delivery slice 6)

- [ ] 5.1 Write red `SeasonTimelineTest` with fixed `Instant`s: next action for each phase combination, announcements at season start, 7 days before the end, 24h, 1h and 5m exactly once each, no-next-season warning, WarnNoNext daily; verify it fails
- [ ] 5.2 Implement `SeasonTimeline` (pure, `Clock`-free, takes `Instant`s) returning `None`, `Announce`, `Prepare`, `Switch`, `Resume` and `WarnNoNext`; verify the tests from 5.1 pass
- [ ] 5.3 Write red `SeasonTransitionTest` with in-memory fakes for `SeasonRunRepository`, `SeasonWorlds` (scriptable success, failure and false unload), `PlayerSeasonReset` and `SeasonAnnouncer`: `worldCreationFailureKeepsCurrentSeasonActive`, `resumeFromPreparedAfterBoundaryClosesOldSeason`, `challengeDeactivationIsCreditedToEndingSeason`, `persistsPhaseBeforeEachSideEffect`; verify they fail
- [ ] 5.4 Implement `SeasonTransition` (one step per phase, persist before and after each side effect, idempotent step checks) and verify the tests from 5.3 pass
- [ ] 5.5 Write red `SeasonServiceTest`: `secondTickDuringTransitionIsANoOp`, `advanceAndRetryNeverRunTwoTransitionsAtOnce`, `databaseDownRefusesEveryTransition`, `postponeShiftsOverlappingSeasons`; verify they fail
- [ ] 5.6 Implement `SeasonService` (implements `SeasonAdminUseCase` and `CurrentSeason`, single-flight guard, cached active season, transition listeners, `SeasonTransitionListener#onSwitched` fired after the switch and before players move) and verify the tests from 5.5 pass
- [ ] 5.7 Write red `SeasonServiceTest` cases for the restart table (B `PREPARING` with and without an existing folder, B `PREPARED` before and after T, B `ACTIVE` with A `CLOSING`, A `ARCHIVING` with both folders present); verify they fail, then implement the resume mapping and verify they pass
- [ ] 5.8 Write red `SeasonServiceTest` cases `advanceConfirmSchedulesNoEarlierThanMinNotice` (the boundary is at least `season.advance.minNotice` after the command and the announcements are sent) and `advanceNowConfirmLogsActor` (a captured appender asserts the admin and the instant); verify they fail
- [ ] 5.9 Implement the minimum-notice scheduling and the `now confirm` path in `SeasonService` and verify the 5.8 tests pass

## 6. Platform world rotation and lobby (delivery slice 7)

- [ ] 6.1 Write red `BukkitSeasonWorldsTest` (MockBukkit v4): evacuation moves players to the target world; unload returns false while a player is inside; the archive move leaves files in place when it fails; verify it fails (creation is covered by the bootstrap test if MockBukkit does not support `createWorld`)
- [ ] 6.2 Implement `BukkitSeasonWorlds` (`WorldCreator.ofKey`, seed, generator per preset, `forcedSpawnPosition`, main-thread and `isTickingWorlds` rules, async archive move and manifest write) and verify the tests from 6.1 pass
- [ ] 6.3 Write red `SeasonJoinListenerTest`: a player whose last world is an unloaded season world is routed to the active season world; a player already in the active world is not moved; the one-time reset happens once per player per season using the `skygridng:season` PDC marker; verify it fails
- [ ] 6.4 Implement `SeasonJoinListener` (on `PlayerSpawnLocationEvent`), `BukkitPlayerSeasonReset` (inventory, ender chest, XP, health, hunger, effects, respawn point, survival mode) and verify the tests from 6.3 pass
- [ ] 6.5 Write red `SeasonTickerTest` with a fixed injected `Clock`: a tick calls `tick(clock.instant())` every 600 ticks and holds no logic of its own; verify it fails
- [ ] 6.6 Implement `SeasonTicker` (`runTaskTimer` every 600 ticks) and `SeasonFeature`/`SeasonModule` wiring that calls `tick` on `ServerLoadEvent` and does not create worlds in `onEnable`; verify the test from 6.5 passes
- [ ] 6.7 Write red bootstrap test `SkyGridNgPluginTest`: the plugin enables with an empty `seasons/` directory and behaves as legacy with no transition scheduled; verify it fails
- [ ] 6.8 Implement the season-scoped `Predicate<World>` that narrows challenge logic to the active season world, and the void lobby as the `level-name` world in the bundled defaults; verify the test from 6.7 passes and `./gradlew :platforms:paper:test --max-workers=2` is green
- [ ] 6.9 Write red test that challenge behaviours do not fire in a prepared but not yet active season world; verify it fails, then verify it passes after 6.8
- [ ] 6.10 Write red `SeasonJoinListenerTest` cases: a player joining 3 days before the season end gets exactly one chat line with the absolute end date and the deleted and kept lists, and a player joining 10 days before gets none; verify they fail
- [ ] 6.11 Implement the final-week join line in `SeasonJoinListener` and verify the 6.10 tests pass

## 7. Season commands (delivery slice 8)

- [ ] 7.1 Write red `SeasonCommandsTest` with `RecordingCommandSource`: `season info` delegates and fires `Messages.seasonInfo`; `season history` for another player is denied without `skygridng.admin`; `season list|status|retry` delegate; verify it fails
- [ ] 7.2 Write red tests that `advance`, `postpone` and `reload` without `confirm` only warn and do not change state, and that with `confirm` they delegate; verify they fail
- [ ] 7.3 Write red test that `challenge tier reset confirm` clears only the active season and that the unconfirmed form clears nothing; verify it fails
- [ ] 7.4 Implement `SeasonCommands` (Cloud annotations, `CommandSource`, no Bukkit), the `Messages.season*` factory methods and the keys `season.info`, `season.history.entry`, `season.status`, `season.announce.upcoming`, `season.announce.started`, `season.advance.confirm`, `season.transition.failed`, `season.no_next` in `Bundle_en.properties` and `Bundle_de.properties`; verify the tests from 7.1 and 7.2 pass
- [ ] 7.5 Register the `season-ids` suggestion provider in `CloudCommandRegistrar` and change `ChallengeCommands` to the season-scoped tier reset; verify the tests from 7.3 pass
- [ ] 7.6 Write red tests that `Messages.season*` renders in the `de` bundle for a `de` client and in `en` otherwise, with absolute dates in the viewer locale; verify they fail, then add the bundle entries and verify they pass
- [ ] 7.7 Write red test that no sidebar or boss-bar countdown is rendered during a running season; verify it fails, then verify it passes with the announcement wiring
- [ ] 7.8 Write red `SeasonCommandsTest` cases that `season info` shows the absolute end date and the deleted and kept lists, and that `season advance now confirm` delegates while `season advance confirm` does not start the transition; verify they fail
- [ ] 7.9 Implement the reset-scope lines in `season info`, the `season.reset.deleted`, `season.reset.kept` keys in both bundles and the `now confirm` branch in `SeasonCommands`; verify the 7.8 tests pass
- [ ] 7.10 Write red tests that each season announcement names the deleted list, the kept list and the recipient's own pending mailbox count, and that the season start and T-7d announcements are sent; verify they fail
- [ ] 7.11 Implement the announcement content in `Messages.season*`, `BukkitSeasonAnnouncer` and the `season.final_week.join` key, and verify the 7.10 tests pass

## 8. Admin documentation, CloudNet and pull request (delivery slice 9)

- [ ] 8.1 Document season operation in `docs/ADMIN.md` (season files, `season status|advance|retry|postpone|reload`, the cut-over steps, the backup before the progress migrations, the archive directory); verify each documented command is listed in `SeasonCommands` and the `Messages` keys exist in both bundles
- [ ] 8.2 Document the CloudNet static service requirement (persistent `world/` and archive directory, no sync back into templates) in `docs/ADMIN.md`; verify the section names the static service and the archive hand-off for BlueMap
- [ ] 8.3 Run `./gradlew build --max-workers=2` and confirm the ArchUnit rules and the migration test are green; verify the build passes
- [ ] 8.4 Open the pull request titled `feat(seasons): rotate season worlds with per-season progress`

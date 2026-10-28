# Proposal

Ships as `feat(seasons)`: one Conventional Commit type and scope for the whole change. The
change implements Epic I3 (season lifecycle) of `docs/USERSTORIES.md` as specified in
`docs/design/seasons.md`.

**Dependency:** this change requires the Flyway per-adapter fix (PR #9, one Flyway location and
one history table per adapter) to be merged on `main` first. Persistence tasks build on it.

## Why

SkyGrid-ng runs one world with no planned end, so there is no season boundary, no fresh start for
players and no record of what players achieved in an earlier period. Server owners need seasons
that are defined in config, switch on schedule into a new SkyGrid world, move players safely, and
keep past tiers and task completions under their season id. A season then becomes a predictable,
transparent fresh start instead of a manual reset. This is the foundation the prestige (I4) and
economy (J1) changes build on.

## What Changes

- Add season definitions: one file per season under `seasons/<id>.<ext>` (JSON, YAML or TOML),
  validated against the bound preset and challenge ids, with a generated
  `schema/season.schema.json`, and an optional `season` block in `config`.
- Add a scheduled season transition: at the end instant of the active season, the next season's
  world is prepared ahead of time with a new seed, the season switch is committed in one database
  transaction, the challenge sets are swapped, players are moved, and the old world is unloaded and
  archived. Every step persists its phase, so a restart resumes the transition without repeating a
  finished step.
- Add platform wiring: season worlds are created, loaded, unloaded and archived on the main thread;
  online players are moved at the switch; joining players are routed into the active season world;
  each player gets a one-time vanilla state reset on first entry to a season.
- Add per-season progress: tier and task completion rows carry a season id and the shared
  `ProgressScope` key (season, player, challenge, prestige step). New progress is written to the
  active season, past seasons are kept as archived rows, and `season history` shows them.
- Add commands: `season info`, `season history [player]` for players; `season list`, `status`,
  `advance`, `retry`, `postpone`, `reload` for admins, with `confirm` gating for destructive or
  time-changing actions (F2 convention). `challenge tier reset` clears the active season only.
- Add persistence: a new `adapters/season` Postgres adapter with its own migration folder and
  history table, and season-scoped migrations for the challenge-progress and task-progress adapters.
- Add operations documentation and a required CloudNet static service, because the season worlds,
  the archive and the plugin data folder must survive a service restart.
- Key product decisions (fixed for this release):
  - The server `level-name` world becomes a small static void lobby. Players join directly into the
    active season world.
  - Each season boundary creates a new SkyGrid world (new seed, the next season's preset). The old
    world is unloaded and moved to the archive directory.
  - Everything resets per season: inventory, ender chest, XP, health, hunger, effects, respawn point,
    game mode, tiers and task progress. Archived data stays under its season id and is neither
    copied nor deleted. The currency balance reset belongs to the economy change.
  - The `ProgressScope` key is introduced here. The prestige change consumes its `prestigeStep`
    component; this change always writes step 0.
- **BREAKING** for server operators: the existing SkyGrid world becomes season `legacy` at the
  cut-over restart, and the main world becomes the void lobby. The cut-over archives the legacy
  world once, by hand.
- **BREAKING** for the repository internals (`ports` is not published): progress repositories
  become season-scoped, and `resetAll()` is replaced by a season-scoped reset.

## Capabilities

### New Capabilities

- `season-lifecycle`: the behaviour of season definitions, the scheduled season transition into a
  fresh world, player placement and per-season reset, season-scoped progress with its archive and
  history, the season announcements and status shown to players and admins, the admin control of
  the lifecycle, and the archived world folders with their manifest.

### Modified Capabilities

- None. `openspec/specs/` contains no capability yet, so every requirement is new.

## Impact

- Modules: `api` (season and `ProgressScope` model, `SkyGridApi#currentSeason()`), `ports`
  (season and run ports, season-scoped progress ports), `core` (timeline, transition state machine,
  season service, progress services), `adapters/config` (season files and schema),
  `adapters/season` (new Postgres run repository), `adapters/challenge-progress` and
  `adapters/task-progress` (migrations), `adapters/command` (season commands and messages),
  `platforms/common` (`platforms.olf.season`), `verification` (ArchUnit rules), `docs/` (admin
  documentation).
- Operations: the server `level-name` world changes to a void lobby; the cut-over restart archives
  the old world by hand; the CloudNet season service must be static.
- Data: new `season_run` table, `season_id` and `prestige_step` columns on progress tables, all
  existing rows assigned to season `legacy`.
- Dependencies: no new runtime dependency for the required scope. Paper 26.1 world APIs
  (`WorldCreator.ofKey`, `forcedSpawnPosition`) are used. The optional compressed archive (`.tar.zst`)
  would add a compression library and is a Could item.
- Tests: JUnit 5, AssertJ, MockBukkit v4 and Testcontainers (`postgres:16-alpine`), with an injected
  `Clock` and fixed seeds. Out of scope: an external BlueMap render of the archive (only the archive
  directory hand-off is designed here).

# Spec Delta

## Purpose

Defines how SkyGrid-ng configures seasons, switches them on schedule into fresh worlds, moves and
resets players, keeps finished seasons' progress under their season id, and lets admins control
and inspect the lifecycle.

## ADDED Requirements

### Requirement: Season definition files

The system SHALL load one season per file from `seasons/<id>.<ext>`, where the format (JSON, YAML or
TOML) is chosen by the file extension, and the file id is authoritative. The system SHALL write
`schema/season.schema.json` next to the other configuration schemas.

#### Scenario: Season file in YAML is loaded

- **WHEN** `seasons/s01_overgrowth.yml` exists and declares a valid id, display name, start, duration, preset and challenge list
- **THEN** the system loads the season with that id
- **AND** the system writes `schema/season.schema.json`

#### Scenario: Season file in an unsupported extension is ignored

- **WHEN** `seasons/example.yml.disabled` exists in the `seasons/` directory
- **THEN** the system does not load it as a season

### Requirement: Invalid season rejection

The system SHALL reject a season that names an unknown preset or an unknown challenge id, log the file, the bad value and the known values, and keep loading the other seasons.

#### Scenario: Unknown challenge id

- **WHEN** a season file lists a challenge id that does not exist in `challenges/`
- **THEN** the system rejects that season
- **AND** the log names the season file, the unknown challenge id and the known challenge ids
- **AND** the other valid seasons are still loaded

#### Scenario: Unknown preset

- **WHEN** a season file names a preset that does not exist in `presets/`
- **THEN** the system rejects that season
- **AND** the log names the season file, the unknown preset and the known presets

### Requirement: Overlapping seasons rejection

The system SHALL reject the later-starting season when two season time windows overlap, and the log SHALL name both season ids.

#### Scenario: Two seasons overlap

- **WHEN** season `s02` starts before season `s01` has ended
- **THEN** the system rejects `s02`
- **AND** the log names both `s01` and `s02`

### Requirement: Legacy behaviour without season files

Where the `seasons/` directory is empty or absent, the system SHALL behave as before seasons existed: one implicit season `legacy`, no world rotation and no scheduled transition.

#### Scenario: Empty seasons directory

- **WHEN** the `seasons/` directory exists and contains no season file
- **THEN** the system runs the implicit `legacy` season
- **AND** no season transition is scheduled

#### Scenario: Absent seasons directory

- **WHEN** the `seasons/` directory does not exist
- **THEN** the system runs the implicit `legacy` season
- **AND** no season transition is scheduled

### Requirement: Reload affects future seasons only

When an admin confirms a season reload, the system SHALL re-read the `seasons/` directory and apply the changes to future seasons only, and SHALL NOT change the preset or seed of the running season.

#### Scenario: Reload confirmed

- **WHEN** an admin runs `/skygrid season reload confirm`
- **THEN** the system re-reads `seasons/`
- **AND** the preset and seed of the running season stay unchanged

#### Scenario: Reload not confirmed

- **WHEN** an admin runs `/skygrid season reload` without `confirm`
- **THEN** the system warns what the reload will change and does not re-read `seasons/`

### Requirement: Scheduled season transition

When the injected clock reaches the end instant of the active season and a next season is configured, the system SHALL run the season transition sequence.

#### Scenario: Transition at the end instant

- **WHEN** the clock reaches the end instant of the active season and the next season is configured
- **THEN** the system starts the season transition

#### Scenario: No transition before the end instant

- **WHEN** the clock is one millisecond before the end instant of the active season
- **THEN** the system does not start the season transition

### Requirement: Gap between seasons

If no next season is configured when the active season reaches its end instant, the system SHALL keep the active season running, extend it, and warn admins once per day until a next season exists.

#### Scenario: No next season at the end instant

- **WHEN** the active season reaches its end instant and no next season is configured
- **THEN** the active season keeps running
- **AND** admins receive a warning once per day until a next season is configured

### Requirement: Fresh world per season

The system SHALL create the next season's world with the next season's preset, under the world key `skygridng:season_<id>`, using a new seed when the season does not fix one.

#### Scenario: New world for the next season

- **WHEN** the transition prepares season `s02` with preset `standard` and no fixed seed
- **THEN** the system creates the world under the key `skygridng:season_s02`
- **AND** the world uses a new seed that differs from the seed of the previous season's world

#### Scenario: Fixed seed in the season file

- **WHEN** season `s02` declares a seed
- **THEN** the world of `s02` is created with that seed

### Requirement: Persisted transition facts

The system SHALL persist the seed and the world key of the next season before it creates that world, so that a restart during the transition recreates the identical world.

#### Scenario: Restart after the seed was persisted

- **WHEN** the server stops after the seed and world key were persisted but before the world was created
- **THEN** after restart the system creates the world with the persisted seed and world key

### Requirement: Failed world creation keeps the current season

If creating the next season's world fails, the system SHALL keep the current season active and playable, notify online admins and the log, and retry as defined by the transition failure handling.

#### Scenario: World creation throws an exception

- **WHEN** creating the next season's world throws an exception
- **THEN** the current season stays active and its challenges are unchanged
- **AND** online admins and the log are notified

#### Scenario: Retry after the failure

- **WHEN** a failed world creation is retried automatically after five minutes and succeeds
- **THEN** the transition continues with the prepared season

#### Scenario: Automatic retries are exhausted

- **WHEN** world creation has failed three times
- **THEN** the system stops retrying automatically
- **AND** only an admin `retry` starts another attempt

### Requirement: Resume a transition after restart

While a transition is in progress, the system SHALL resume it from its last persisted phase after a server restart, and SHALL NOT repeat a completed step.

#### Scenario: Restart while the old season is closing

- **WHEN** the server restarts after the switch to the new season was committed but before the old world was unloaded
- **THEN** the system resumes at the step that moves players and unloads the old world
- **AND** the system does not switch the season a second time

#### Scenario: Restart after archiving finished

- **WHEN** the server restarts and the persisted phase of the old season is `ARCHIVED`
- **THEN** the system does not unload or archive the old world again

### Requirement: Exact challenge set per season

When the transition switches seasons, the system SHALL activate exactly the new season's challenge set and deactivate every other challenge.

#### Scenario: Switch to a season with two challenges

- **WHEN** the transition switches to a season whose challenge set is `one_life` and `no_crafting_table`
- **THEN** exactly `one_life` and `no_crafting_table` are active
- **AND** every other challenge is inactive

#### Scenario: Challenge deactivation is credited to the ending season

- **WHEN** the transition deactivates the challenges of the ending season
- **THEN** the round-boundary results are recorded under the ending season id

### Requirement: Pre-created world lead time

The system SHALL pre-create the next season's world a configurable lead time before the boundary, with a default of 15 minutes, so that the boundary itself does not wait for world generation.

#### Scenario: Default lead time

- **WHEN** no lead time is configured and the next season starts at instant T
- **THEN** the system starts preparing the next world at T minus 15 minutes

#### Scenario: Configured lead time

- **WHEN** the configured lead time is 30 minutes
- **THEN** the system starts preparing the next world at T minus 30 minutes

### Requirement: Online players move at the switch

When the season switches, the system SHALL move every online player from the old season world to a spread-spawn slot in the new season world, following the first-join spawn rules.

#### Scenario: Players online at the switch

- **WHEN** three players are in the old season world at the switch
- **THEN** each of them is moved to a spread-spawn slot in the new season world

### Requirement: Late joiners land in the active season

When a player joins whose last location is in an unloaded season world, the system SHALL place the player at a spread-spawn slot in the active season world.

#### Scenario: Player rejoins after the old world was archived

- **WHEN** a player whose last location was in season `s01` joins during season `s02`
- **THEN** the player is placed at a spread-spawn slot in the `s02` world

### Requirement: Vanilla state reset once per season

When a player first enters a season, the system SHALL reset the player's vanilla state (inventory, ender chest, experience, health, hunger, effects, respawn point and game mode set to survival), exactly once per player per season.

#### Scenario: First entry into a season

- **WHEN** a player enters season `s02` for the first time
- **THEN** the player's vanilla state is reset
- **AND** the player's game mode is survival

#### Scenario: Second entry into the same season

- **WHEN** a player who already has the reset marker for `s02` joins `s02` again
- **THEN** the player's vanilla state is not reset again

### Requirement: Unload retry without touching loaded worlds

If unloading the old season world fails because it still holds players, the system SHALL move the remaining players and retry, and SHALL NOT delete or move the files of a loaded world.

#### Scenario: Player still in the old world at unload

- **WHEN** the unload of the old world returns false because a player is still inside
- **THEN** the system moves that player to the new season world
- **AND** the system retries the unload on a later tick
- **AND** no world files are deleted or moved while the world is loaded

### Requirement: Season-scoped progress

The system SHALL key every tier and task completion row by season id, and SHALL write new progress to the active season only.

#### Scenario: Progress recorded during a season

- **WHEN** a player completes a task while season `s02` is active
- **THEN** the completion is stored with season id `s02`

#### Scenario: Progress after the switch

- **WHEN** a player completes a task after the switch to season `s03`
- **THEN** the completion is stored with season id `s03` and the earlier rows are unchanged

### Requirement: Progress scope key

The system SHALL identify season progress by the scope key (season, player, challenge, prestige step), and SHALL accept a prestige step only between 0 and 10 inclusive.

#### Scenario: Prestige step out of range

- **WHEN** a progress scope is created with prestige step 11
- **THEN** the system rejects the scope

#### Scenario: Prestige step not used yet

- **WHEN** progress is recorded before the prestige feature exists
- **THEN** the prestige step is 0

### Requirement: Archive by key at season end

When a season ends, the system SHALL keep its progress rows unchanged, archived by season id, with no copy and no delete.

#### Scenario: Season ends

- **WHEN** season `s01` ends and season `s02` becomes active
- **THEN** the rows of `s01` are not modified or deleted

### Requirement: Player season history

When a player runs `/skygrid season history [player]`, the system SHALL list that player's tier and completed task count for each season with progress.

#### Scenario: Player views own history

- **WHEN** a player with progress in `legacy` and `s01` runs `/skygrid season history`
- **THEN** the output lists the tier and completed task count for `legacy` and for `s01`

#### Scenario: Admin views another player's history

- **WHEN** an admin runs `/skygrid season history <player>`
- **THEN** the output lists that player's history

#### Scenario: Player views another player's history without permission

- **WHEN** a player without the admin permission runs `/skygrid season history <other player>`
- **THEN** the system denies the command

### Requirement: Tier reset affects the active season only

When an admin confirms `/skygrid challenge tier reset confirm`, the system SHALL clear the progress of the active season only.

#### Scenario: Confirmed tier reset

- **WHEN** an admin runs `/skygrid challenge tier reset confirm` during season `s02`
- **THEN** the progress rows of `s02` are cleared
- **AND** the progress rows of `s01` remain

#### Scenario: Unconfirmed tier reset

- **WHEN** an admin runs `/skygrid challenge tier reset` without `confirm`
- **THEN** no progress row is cleared

### Requirement: Legacy migration of existing rows

When existing data is migrated, the system SHALL assign all pre-season rows to the season `legacy`.

#### Scenario: Existing rows after migration

- **WHEN** the season migration runs on a database with existing tier and task rows
- **THEN** every pre-existing row has season id `legacy` and prestige step 0

### Requirement: Season info display

When a player runs `/skygrid season info`, the system SHALL show the season display name, its active challenges and its end date as an absolute date in the viewer's locale.

#### Scenario: Player views the running season

- **WHEN** a player runs `/skygrid season info` during season `s01`
- **THEN** the output shows the display name, the active challenges and the end date of `s01`
- **AND** the end date is formatted in the viewer's locale

### Requirement: Upcoming transition announcements

The system SHALL announce an upcoming transition at most three times, at 24 hours, 1 hour and 5 minutes before the boundary, and each announcement SHALL state that progress is archived, not lost.

#### Scenario: Three announcements per transition

- **WHEN** the boundary of a transition is approaching
- **THEN** exactly one announcement is sent at 24 hours, one at 1 hour and one at 5 minutes before the boundary
- **AND** each announcement states that progress is archived, not lost

#### Scenario: No countdown timer

- **WHEN** a season is running
- **THEN** no sidebar or boss-bar countdown timer is shown

### Requirement: Localized season messages

The system SHALL render every season message from the `en` and `de` message bundles in the viewer's client locale.

#### Scenario: German client

- **WHEN** a player whose client locale is `de` receives a season message
- **THEN** the message is rendered from the `de` bundle

#### Scenario: Unsupported client locale

- **WHEN** a player whose client locale is not supported receives a season message
- **THEN** the message is rendered from the `en` bundle

### Requirement: Season status for admins

When an admin runs `/skygrid season status`, the system SHALL show the active season, its phase, the next season and the next scheduled action with its instant.

#### Scenario: Status during a transition

- **WHEN** an admin runs `/skygrid season status` while the next world is being prepared
- **THEN** the output shows the active season, its phase, the next season and the next scheduled action with its instant

### Requirement: Confirmed season advance

When an admin runs `/skygrid season advance`, the system SHALL only warn and name what will happen. When the admin runs `/skygrid season advance confirm`, the system SHALL start the transition immediately.

#### Scenario: Advance without confirm

- **WHEN** an admin runs `/skygrid season advance`
- **THEN** the system names the season change that will happen and does not start the transition

#### Scenario: Advance with confirm

- **WHEN** an admin runs `/skygrid season advance confirm`
- **THEN** the system starts the transition now

### Requirement: Retry a failed transition

When an admin runs `/skygrid season retry`, the system SHALL resume a failed transition from its persisted phase.

#### Scenario: Retry after world creation failed

- **WHEN** an admin runs `/skygrid season retry` after the preparation of the next season failed
- **THEN** the system resumes the preparation from the persisted phase

### Requirement: Postpone the season end

When an admin runs `/skygrid season postpone <duration> confirm`, the system SHALL move the end of the active season, shift the following seasons that would overlap, and announce the new end.

#### Scenario: Postpone confirmed

- **WHEN** an admin runs `/skygrid season postpone PT2H confirm`
- **THEN** the end of the active season moves by two hours
- **AND** the new end is announced

#### Scenario: Postpone not confirmed

- **WHEN** an admin runs `/skygrid season postpone PT2H` without `confirm`
- **THEN** the end of the active season does not change

### Requirement: Refuse transitions without the database

If the database is unreachable, the system SHALL refuse every transition, report the reason in `status`, and keep the current world running.

#### Scenario: Database down at the boundary

- **WHEN** the boundary is reached and the database is unreachable
- **THEN** no transition step runs
- **AND** the current world keeps running
- **AND** `/skygrid season status` reports the database as the reason

### Requirement: Archive the old world as a manifest folder

When the old world is unloaded, the system SHALL move its folder to `<archive.directory>/<seasonId>/` and write a `season.json` manifest with the id, display name, seed, preset, start, end, world key and plugin version.

#### Scenario: Old world archived

- **WHEN** season `s01` has been unloaded and the transition continues
- **THEN** the folder of `s01` is moved to `<archive.directory>/s01/`
- **AND** `season.json` in that folder lists the id, display name, seed, preset, start, end, world key and plugin version

### Requirement: Archive failure keeps the new season playable

If the archive move fails, the system SHALL leave the folder in place, mark the season run `ARCHIVE_FAILED`, and keep the new season fully playable.

#### Scenario: Archive move fails

- **WHEN** moving the folder of the old season fails
- **THEN** the folder stays in its original place
- **AND** the season run is marked `ARCHIVE_FAILED`
- **AND** the players can play the new season normally

### Requirement: Compressed archive

Where `archive.compress` is set to true, the system SHALL write the archive as a `.tar.zst` file instead of a folder.

#### Scenario: Compression enabled

- **WHEN** `archive.compress` is true and the old world is archived
- **THEN** the archive is written as a `.tar.zst` file in the archive directory

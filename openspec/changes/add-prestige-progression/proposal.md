# Proposal

Conventional Commit: `feat(prestige)`. Ships as the code pull request
`feat(prestige): let players opt into bounded prestige steps`.

Depends on: change `add-season-lifecycle` (season ids and the shared `ProgressScope` key
`(season, player, challenge, prestige step)`). This change consumes that key and does not migrate
the task completion table itself. The Flyway fix (one migration location and one history table per
adapter) is merged before the persistence slice.

## Why

Players who have finished a challenge's goals have nothing left to play for on replay: the same
rules, the same tasks, the same outcome. Prestige gives them an optional, clearly described harder
variant of the same challenge, so that mastery can be replayed as a voluntary, self-set challenge.
The product owner decided that the player chooses the step, the admin defines what each step
changes within hard limits, and the game never decides difficulty or rewards from how a player
behaves.

## What Changes

- Players keep a current prestige step per challenge and season. Step 0 is the challenge as it ships
  today and is the default for everyone. The all-time highest step is kept as a record.
- `/skygrid prestige up <challenge>` shows the next step's complete modifier list, states that its
  tasks start fresh, and waits for `confirm`. Nothing changes before confirmation.
- A raise by one step requires every configured task of the challenge to be completed at the current
  step. It is rejected while the challenge is running, at the ladder's last step, or while a task is
  missing; the rejection names the missing tasks or the reason.
- `/skygrid prestige down <challenge>` lowers the step by one at any time, without confirmation. It
  never touches the highest step or any recorded task completion.
- Task completions are recorded per progress scope (season, player, challenge, prestige step). A task
  completed at step N stays completed at step N after any raise or lower. A task reward is granted at
  most once per player, challenge, task and step, with the same reward definition at every step.
- While a prestige step above 0 is current, its modifiers apply to that player only, and only in a
  SkyGrid world while the challenge is active. Modifiers are a fixed list of typed, bounded
  difficulty modifiers (for example `max_health`, `damage_taken_multiplier`, `denied_stations`).
- Admins define up to 10 contiguous steps per challenge in an optional `prestige` block of
  `challenges/<id>.<ext>`. A ladder that breaks a hard limit, is not strictly harder than the
  previous step, or uses a modifier its challenge does not support disables prestige for that
  challenge only, with a logged warning. The block appears in the generated challenge schema.
- Prestige is always visible: the challenge sidebar shows the step and a one-line modifier summary,
  a player with a step above 0 receives one chat line on join, and `/skygrid prestige info` lists
  the current, highest and maximum step, the current and next modifiers, and whether the raise is
  unlocked.
- Optional, once per player, challenge and step: a neutral chat line when prestige becomes available.
  Admins can disable it per challenge.
- Admins can inspect a player's steps (`/skygrid prestige of <player> [challenge]`) and reset every
  player's current step in the active season (`/skygrid prestige reset confirm`), keeping the
  all-time highest record.
- All player-facing text is translatable and ships in English and German.
- Never: rewards, reward magnitudes, modifiers or steps are derived from session length, completion
  ratio, drop-off point, return rate, deaths or any other behavior signal. These signals are not
  collected or used in this release. There are no automatic step changes, no per-step reward
  overrides and no cross-challenge prestige.
- `level_is_border` supports the modifier type `blocks_per_level`, but its bundled default ships
  without a prestige ladder until its border is enforced, because an unenforced "harder" value is not
  real difficulty.

Not in this change: the seasons themselves (`add-season-lifecycle`), the economy, behavior-signal
suggestions, and a public prestige leaderboard (it would need its own I5 review).

## Capabilities

### New Capabilities
- `prestige-progression`: per-player, per-season prestige steps for challenges, the admin-defined
  bounded difficulty ladders they are built from, the raise and lower rules, the visibility of the
  current step, and the rule that rewards and steps never depend on behavior signals.

### Modified Capabilities
- None. The task completion behaviour changes only through the progress scope key introduced by
  `add-season-lifecycle`; no existing main spec under `openspec/specs/` is changed here.

## Impact

- `api`: `DifficultyModifier` (sealed, records), `RegainSource`, `PrestigeStep`, `PrestigeLadder`,
  `PlayerPrestige`; `ChallengeRule` gains an optional ladder.
- `ports`: `PrestigeRepository` (out), `PrestigeUseCase` (in); `TaskProgressUseCase` signatures are
  unchanged.
- `core`: `PrestigeLevels` cache, `PrestigeService`, and a `TaskProgressService` that records and
  reads completions at the player's current step.
- `adapters/config`: optional `prestige` block in challenge files, schema regeneration, per-ladder
  failure isolation.
- `adapters/prestige` (new module): PostgreSQL repository with its own Flyway folder
  `db/migration/prestige` and history table `flyway_history_prestige`.
- `adapters/command`: `prestige` subcommands, a suggestion provider, help text.
- `adapters/scoreboard`: step suffix and modifier summary in the challenge sidebar.
- `platforms/common`: wiring, challenge behaviours that apply modifiers, the join, quit and unlock
  feature, and English and German messages.
- `platforms/paper`: `plugin.yml` permission `skygridng.prestige` (default true).
- `verification`: the new adapter module is added to the ArchUnit and migration checks.
- No change to existing player data: existing task completions are read as step 0.

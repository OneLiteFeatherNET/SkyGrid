# SkyGrid-ng: User Stories

Functional requirements for the finalized project, written from the perspective of the
people who use it. Format: As a ROLE, I want GOAL, so that BENEFIT. Each story lists
acceptance criteria.

## Epic A: SkyGrid revival

### A1: Classic SkyGrid world
As a **server owner**, I want an infinite SkyGrid world (a grid of single blocks every
4 meters floating in the void), so that I can revive the classic SethBling SkyGrid survival
experience on my server.
- The world is empty (void) except for single blocks on a regular 3D grid.
- Grid spacing is configurable (default 4).
- Block selection is seed based: the same world seed and coordinates always produce the
  same block.
- The block distribution is configurable via a weighted table.

### A2: Coherent (clustered) SkyGrid
As a **server owner**, I want an alternative SkyGrid where similar blocks cluster into veins
or themed regions, so that the world feels less uniformly random and more explorable.
- Same grid positions as A1.
- Block choice is biased by seed based value noise so neighboring grid blocks tend to be the
  same type.
- Cluster size is configurable (noise scale).

### A3: Configurable block table
As a **server admin**, I want to define which blocks appear and how often, so that I can tune
the difficulty and theme.
- Each entry has a material name and a weight.
- Unknown material names degrade gracefully (a safe fallback block, no crash).

## Epic B: Switchable generation algorithms

### B1: Choose algorithm by config
As a **server admin**, I want to select the generation algorithm in the config, so that I can
pick classic SkyGrid or clustered SkyGrid for a world.
- Config key `algorithm` accepts `skygrid`, `noise_skygrid`.
- An unknown value falls back to a documented default (`skygrid`) with a warning.

### B2: Switch algorithm in game
As a **server admin**, I want to switch the active algorithm at runtime, so that I can
experiment without editing files.
- `/skygrid algorithm <id>` changes the active algorithm.
- New chunks use the new algorithm; the change is reversible with undo.

### B3: Faster seed based generation
As a **server owner with many players**, I want the SkyGrid algorithms to be fast, so that
chunk generation does not lag the server.
- SkyGrid generation is O(1) per grid cell (no constraint propagation).
- Generation is deterministic and parallel safe.

## Epic C: Safe first-join spawn (spread, no starter platform)

### C1: Spread new players apart
As a **server owner**, I want each new player assigned a distinct area on first join, so that
players do not land on top of each other in the void.
- New players get a spiral-outward-assigned slot, one per join, per world.
- The assignment is a pure function of join order; the same join order always produces the
  same sequence of slots.
- Disabled via config (`spread: false`) falls back to a single shared spawn area.

### C2: Land on a safe block, not a platform
As a **server owner**, I want a new player to land standing on a real, non-damaging block near
their assigned slot, so that first join never drops them into the void or into hazards, without
building a permanent platform structure into the world.
- On first join, the plugin searches near the assigned slot for a column with solid, non-hazardous
  ground and passable space at foot and head height, and teleports the player there.
- Search breadth is configurable (`searchRadius`, `maxAttempts`); if every attempt fails, the
  player is placed at a fixed high point above the slot rather than the join failing.
- No block is placed and no entity is spawned as part of this search; the world is left exactly as
  generation produced it.

## Epic D: Internationalization

### D1: Localized messages
As a **non English speaking admin**, I want command feedback in my language, so that the
plugin is usable for my team.
- All player facing command messages are translatable.
- Messages render in the viewer's client locale (server side translation via Adventure).
- English and German are shipped; more locales can be added by dropping in a bundle.

### D2: English code base
As a **maintainer**, I want the entire code base (identifiers, comments, docs) in English, so
that international contributors can work on it.
- No non English comments or identifiers remain.
- User facing strings live in translation bundles, not in code.

## Epic E: Architecture and extensibility (retained)

### E1: Framework independent core
As a **maintainer**, I want the generation core to be free of Bukkit and JSON dependencies, so
that it stays testable and portable.
- `core/` has no `org.bukkit` or `com.google.gson` imports.
- Algorithms are unit testable without a server.

### E2: Pluggable algorithm port
As a **developer**, I want algorithms behind a single port, so that I can add a new one without
touching existing code.
- A new algorithm is one class implementing `TerrainAlgorithm` plus one factory branch.
- The chunk generator does not change when an algorithm is added.

## Epic F: Admin command UX

### F1: Actionable, discoverable command feedback
As a **server admin**, I want `/skygrid` command feedback to explain what went wrong and what
changed, so that I can fix mistakes without guessing and understand a command's consequences
before I run it.
- Every error message echoes the invalid value that caused it (unknown material, invalid number).
- `reload` and `save` messages state their actual consequence (reload discards the undo history,
  save permanently overwrites the rule file).
- `/skygrid help <subcommand>` shows a focused usage line instead of one dense line listing every
  subcommand at once.
- Tab completion for `/skygrid algorithm` suggests ids from the same registry the algorithm switch
  uses, so a newly added algorithm cannot drift out of sync with the suggestions.
- Tab completion for `/skygrid skyweight` suggests material ids.
- Tab completion only suggests subcommands and arguments to a player holding `skygridng.admin`,
  matching the permission already enforced on execution.

### F2: Confirmation for destructive actions
As a **server admin**, I want a confirmation step before a command that discards unsaved rule
changes, so that I don't lose in-game edits by accident.
- `/skygrid reload` requires an explicit confirmation (for example `/skygrid reload confirm`)
  before discarding the undo history.
- The warning shown before confirmation names exactly what will be lost.

## Epic I: Challenges and Seasons

### I1: Toggleable challenge rules
As a **server admin**, I want to enable individual BastiGHG-style challenge rules (e.g. "no
crafting table"), so that I can offer a harder, more focused survival experience without editing
code.
- Challenge rules are defined in config (`challenges/`, one YAML file per rule).
- `/skygrid challenge list|toggle <id>` lists and toggles rules; toggling persists immediately.
- Enforced by an `OlfFeature` in `plugin`'s `platforms.olf` package (a naming convention
  marking OneLiteFeather-server-specific mechanics, not a separate distribution).

### I2: Goal and achievement tracking within a challenge
As a **player**, I want visible goals within an active challenge, so that I know what I'm working
toward beyond surviving under restrictions.
- Each Challenge now optionally holds an ordered list of Tasks (`api.model.Task`: `id`,
  `description`, an optional `reward`), config-defined per challenge
  (`challenges/<id>.<ext>`'s `tasks` list, `sequential: true|false`). `sequential: true` enforces
  that a later task only counts once every earlier task in the configured list order is complete
  for that player (a safety net, not the primary mechanism: task completion is still decided by
  each challenge's own `ChallengeBehavior` Java logic, never a config condition DSL); `false`
  accepts any task in any order.
- A reward is config-defined per task (`api.model.Reward`, a sealed interface: `ItemGrant`,
  `CommandDispatch`, `PlayerMessage`) and grants exactly once per player per task, the moment
  `ports.in.TaskProgressUseCase#completeTask` records a genuinely new completion (idempotent: a
  repeat call, or a sequential-order rejection, is a no-op, never a duplicate grant).
- Task completion is durable per player (PostgreSQL, `plugin`,
  `ports.out.TaskCompletionRepository`, Flyway-migrated `task_completion` table keyed on
  `(player_id, challenge_id, task_id)`, row existence is the only fact stored, same
  data-minimization stance as `challenge_tier`), fronted by `TaskProgressUseCase` /
  `core.service.TaskProgressService`; independent of and unaffected by the pre-existing tier
  counter (I2a).
- A player's task progress for the first active challenge (catalog order, a display
  simplification when more than one challenge is active) is always visible in-game via a live
  per-player scoreboard sidebar (`platforms.olf.scoreboard`, `platforms.olf.ChallengeSidebarFeature`):
  built on join from `TaskProgressUseCase#tasksFor`, updated in place (no repeating task, no
  full re-render) via `TaskProgressUseCase#addCompletionListener` whenever a task flips to done.
- Wired for three challenges in this slice: `one_life` (`survive_the_round`, extending the
  existing round-boundary hook in `OneLifeListener`), `no_crafting_table`
  (`no_crafting_table_round`, a new round-boundary hook plus a per-round violation set),
  `no_natural_regeneration` (`endured_the_round`, a new round-boundary hook, every online
  matching-world player qualifies, an accepted attendance-not-achievement skeleton for this
  slice). `level_is_border` intentionally ships with no tasks: it has no round/elimination/
  violation concept today.
- Reward-granting (`ports.out.RewardGrantor`, implemented by `platforms.olf.challenge.RewardGrantorImpl`)
  is Bukkit-aware only in the adapter: an item into the player's inventory, a server command with
  `{player}` substituted (run as console, scheduled onto the main thread), or an admin-authored
  MiniMessage chat line. Only the `message` reward type ships real content in this slice (all
  three are implemented and tested); `one_life`'s task reward replaced its old direct tier-earned
  chat line rather than firing both, so a survivor sees exactly one message per round, not two.

### I2a: Per-player challenge tier persistence (`one_life` first slice)
As a **player**, I want my progress in a challenge to survive server restarts and be visible on
request, so that repeated play is recognized instead of forgotten every reboot.
- First database-backed persistence in the project: a PostgreSQL-backed
  `ports.out.ChallengeProgressRepository` (`plugin`, HikariCP pooled,
  parameterized JDBC only, Flyway-migrated `challenge_tier` table keyed on `(player_id,
  challenge_id)`), fronted by `ports.in.ChallengeProgressUseCase` /
  `core.service.ChallengeProgressService`.
- Wired end to end for exactly one challenge, `one_life`: when it transitions from active to
  inactive, every online, non-eliminated player's tier increments by one and they get a
  translatable chat message announcing it; eliminated players keep their tier unchanged.
- `/skygrid challenge tier [player]` reports a tier; `/skygrid challenge tier reset confirm`
  (admin-only, same confirmation convention as F2) clears every player's tier for every challenge,
  standing in for a real season boundary until I3 exists.
- Explicitly deferred (tracked separately, not part of this slice): tier does not yet change
  challenge difficulty or enforcement (that is I4's job once it has its own dark-pattern review
  per I5); the other three challenges (`no_crafting_table`, `no_natural_regeneration`,
  `level_is_border`) are not wired to tiers yet, though the mechanism generalizes to them; there is
  no real Season model yet, `reset` is a deliberately minimal stand-in so it is not confused with
  I3 once that gets designed.
- Credentials (`SKYGRID_DB_HOST`/`PORT`/`NAME`/`USER`/`PASSWORD`) come from environment variables,
  never a config file.

### I3: Season lifecycle
As a **server owner**, I want challenges bundled into fixed-duration Seasons with their own content
package, so that I can deliver new content on a predictable cadence.
- Backlog. Needs a Season model (start/end, bound challenge rule set) layered orthogonally to
  Presets; not implemented yet.

### I4: Adaptive meta-progression
As a **player**, I want repeating/prestige-style progression that scales challenge difficulty to my
own play patterns, so that the game stays appropriately challenging over time.
- Adapts difficulty/pacing to behavior signals (session-length trend, completion ratio, drop-off
  point, voluntary return rate); must never adapt reward magnitude to a struggle/disengagement
  signal (the "compensation loop" risk).
- Backlog. Needs the per-player state store; not implemented yet.

### I5: Ethical engagement review gate
As a **player**, I want every challenge/season/progression mechanic vetted against dark-pattern
risks before release, so that the game stays fun rather than manipulative.
- Every new mechanic passes the checklist in `.claude/skills/challenge-season-system/SKILL.md`,
  reviewed by `player-psychology-designer`, before implementation.

## Epic J: Economy and marketplace (sketch only, not scheduled)

### J1: SkyBlock-style player economy (sketch)
As a **server owner**, I want a configurable player economy, so that progression has a persistent
goods/trade layer.
- Sketch only; needs a full requirements pass before implementation.
- Do not use JSR 354 (`javax.money`/Moneta): it hard-requires `jakarta.annotation` and `okhttp3` at
  runtime, violating the framework-free-core rule. Use a small `Money`-style record in `api.model`
  instead (same pattern as `BlockId`).
- The per-player persistence gap it shared with Epic I2-I4 has a first concrete solution now (see
  I2a); this economy sketch would follow the same repository/use-case pattern rather than
  reinventing one.

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
- Challenge rules are defined in config (`challenges/`, one file per rule, JSON/YAML/TOML).
- `/skygrid challenge list|toggle <id>` lists and toggles rules; toggling persists immediately.
- Enforced by an `OlfFeature` in `platforms/common`'s `platforms.olf` package (a naming convention
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
- Task completion is durable per player (PostgreSQL, `adapters/task-progress`,
  `ports.out.TaskCompletionRepository`, Flyway-migrated `task_completion` table keyed on
  `(player_id, challenge_id, task_id)`, row existence is the only fact stored, same
  data-minimization stance as `challenge_tier`), fronted by `TaskProgressUseCase` /
  `core.service.TaskProgressService`; independent of and unaffected by the pre-existing tier
  counter (I2a).
- A player's task progress for the first active challenge (catalog order, a display
  simplification when more than one challenge is active) is always visible in-game via a live
  per-player scoreboard sidebar (`adapters/scoreboard`, `platforms.olf.ChallengeSidebarFeature`):
  built on join from `TaskProgressUseCase#tasksFor`, updated in place (no repeating task, no
  full re-render) via `TaskProgressUseCase#addCompletionListener` whenever a task flips to done.
- Wired for three challenges in this slice: `one_life` (`survive_the_round`, extending the
  existing round-boundary hook in `OneLifeListener`), `no_crafting_table`
  (`no_crafting_table_round`, a new round-boundary hook plus a per-round violation set),
  `no_natural_regeneration` (`endured_the_round`, a new round-boundary hook, every online
  matching-world player qualifies, an accepted attendance-not-achievement skeleton for this
  slice). `level_is_border` intentionally ships with no tasks: it has no round/elimination/
  violation concept today.
- Reward-granting (`ports.out.RewardGrantor`, implemented by `adapters.challenge.RewardGrantorImpl`)
  is Bukkit-aware only in the adapter: an item into the player's inventory, a server command with
  `{player}` substituted (run as console, scheduled onto the main thread), or an admin-authored
  MiniMessage chat line. Only the `message` reward type ships real content in this slice (all
  three are implemented and tested); `one_life`'s task reward replaced its old direct tier-earned
  chat line rather than firing both, so a survivor sees exactly one message per round, not two.

### I2a: Per-player challenge tier persistence (`one_life` first slice)
As a **player**, I want my progress in a challenge to survive server restarts and be visible on
request, so that repeated play is recognized instead of forgotten every reboot.
- First database-backed persistence in the project: a PostgreSQL-backed
  `ports.out.ChallengeProgressRepository` (`adapters/challenge-progress`, HikariCP pooled,
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

### I3a: Seasons defined in config
As a **server owner**, I want each season described in one config file (dates, duration, preset, challenge set), so that I can plan a season's content without touching code.
- Status: Specified, not implemented (`docs/design/seasons.md`).
- [Must] When the server starts, the system shall load one season per file from `seasons/<id>.<ext>` (JSON, YAML or TOML).
- [Must] If a season names an unknown preset or challenge, or overlaps another season, then the system shall reject that season with a log line naming the file and the bad value, and keep loading the other seasons.
- [Must] Where `seasons/` is empty or absent, the system shall run one implicit `legacy` season without scheduled transitions.
- [Should] When an admin runs `/skygrid season reload confirm`, the system shall apply changes to future seasons only, never to the running season.

### I3b: Automatic season transition with a fresh world
As a **server owner**, I want the server to switch to the next season on schedule, with a new SkyGrid world, so that each season is a clean start without a manual reset.
- Status: Specified, not implemented (`docs/design/seasons.md`).
- [Must] When the active season ends and a next season is configured, the system shall create the next world with a new seed under its own world key, and activate exactly that season's challenge set.
- [Must] The system shall persist each transition phase before and after its side effects, and after a restart shall resume from the last persisted phase without repeating a finished step.
- [Must] If world creation fails, then the system shall keep the current season playable, alert admins and retry.
- [Must] If no next season is configured at the end instant, then the system shall extend the current season and warn admins once per day.
- [Should] The system shall pre-create the next world 15 minutes before the boundary by default.

### I3c: Players move safely into the new season
As a **player**, I want to land safely in the new season's world, whether I am online at the switch or join later, so that I never get stuck in an archived world.
- Status: Specified, not implemented (`docs/design/seasons.md`).
- [Must] When the season switches, the system shall move every online player from the old world to a spread-spawn slot in the new world.
- [Must] When a player joins while their last world is an archived season world, the system shall place them at a spread-spawn slot in the active season world.
- [Must] When a player first enters a season, the system shall reset their vanilla state (inventory, ender chest, XP, health, hunger, effects, respawn point, game mode) exactly once per player per season.

### I3d: Per-season progress archive
As a **player**, I want my tiers and task completions of past seasons kept, so that what I achieved is recognized after the reset.
- Status: Specified, not implemented (`docs/design/seasons.md`).
- [Must] The system shall key every tier and task completion by season id, write new progress only to the active season, and leave ended seasons unchanged.
- [Must] When a player runs `/skygrid season history [player]`, the system shall list that player's tier and completed task count for each past season.
- [Must] When an admin runs `/skygrid challenge tier reset confirm`, the system shall clear the active season's progress only.
- [Must] When existing data is migrated, the system shall assign every pre-season row to season `legacy`.

### I3e: Season visibility and announcements
As a **player**, I want to see which season is running and when it ends, so that I can plan my play without a countdown pressure.
- Status: Specified, not implemented (`docs/design/seasons.md`).
- [Must] When a player runs `/skygrid season info`, the system shall show the season name, its active challenges and its end date as an absolute date in the viewer's locale.
- [Must] The system shall announce an upcoming transition at most three times (24 h, 1 h and 5 min before), each stating that progress is archived, not lost.
- [Won't] A sidebar or boss bar countdown timer in this release.

### I3f: Admin control over the lifecycle
As a **server admin**, I want to inspect, force, retry and postpone a transition, so that I can handle maintenance windows and failures without editing the database.
- Status: Specified, not implemented (`docs/design/seasons.md`).
- [Must] When an admin runs `/skygrid season status`, the system shall show the active season, its phase, the next season and the next scheduled action with its instant.
- [Must] When an admin runs `/skygrid season advance`, the system shall only describe what will happen; with `confirm` it shall start the transition now.
- [Must] When an admin runs `/skygrid season retry`, the system shall resume a failed transition from its persisted phase.
- [Should] When an admin runs `/skygrid season postpone <duration> confirm`, the system shall move the active season's end and announce the new end.
- [Must] If the database is unreachable, then the system shall refuse every transition, report the reason in `status`, and keep the current world running.

### I3g: Archived worlds on disk
As a **server owner**, I want each finished season world kept as a self-describing folder, so that an external renderer or a later restore can use it.
- Status: Specified, not implemented (`docs/design/seasons.md`).
- [Must] When the old world unloads, the system shall move its folder to `<archive.directory>/<seasonId>/` and write a `season.json` manifest (id, name, seed, preset, start, end, world key, plugin version).
- [Must] If the archive move fails, then the system shall leave the folder in place, mark the run `ARCHIVE_FAILED`, and keep the new season playable.
- [Could] Where `archive.compress` is set, the system shall write a `.tar.zst` archive instead of a folder.

### I4: Opt-in prestige per challenge
As a **player**, I want to choose a harder prestige step of a challenge I have already mastered, and to see exactly what changes, so that the challenge stays interesting on replay without the game deciding for me.
- Status: Specified, not implemented (`docs/design/prestige.md`).
- [Must] When a player runs `/skygrid prestige up <challenge>`, the system shall show the next step's full modifier list, that its tasks start fresh, and a confirm action, and shall not change the step before confirmation.
- [Must] When a player confirms a raise and every task of the challenge is complete at the current step, the system shall raise the current step by exactly one.
- [Must] If a task is incomplete at the current step, or the challenge is active, or the step is the ladder maximum, then the system shall reject the raise and say why.
- [Must] When a player runs `/skygrid prestige down <challenge>`, the system shall lower the step at any time without confirmation, keeping every completion and the highest step reached.
- [Must] While a step above 0 is current, the system shall apply exactly that step's modifiers to that player only.
- [Must] The system shall grant each task reward at most once per player, challenge, task and step, with the same reward definition at every step.
- [Must] The system shall never derive a reward, a modifier or a step from session length, completion ratio, drop-off point, return rate or deaths.
- [Should] When a player completes the last open task at the current step and a higher step exists, the system shall send one neutral chat line, unless the admin disabled it for that challenge.
- [Won't] Behavior-signal suggestions, automatic step changes, per-step reward overrides and a cross-challenge global prestige in this release.

### I4a: Admin-defined prestige ladders
As a **server admin**, I want to define prestige steps per challenge within hard limits, so that I control how hard prestige gets and cannot ship an absurd step by mistake.
- Status: Specified, not implemented (`docs/design/prestige.md`).
- [Must] The system shall read an optional `prestige` block from `challenges/<id>.<ext>` and include it in the generated challenge schema.
- [Must] If a ladder has non-contiguous steps, more than 10 steps, a value outside a modifier's bounds, a step that is not strictly harder than the previous one, or a modifier type the challenge does not support, then the system shall disable prestige for that challenge, log a warning naming the file and the step, and keep loading the other challenges.
- [Must] When an admin runs `/skygrid prestige of <player> [challenge]`, the system shall show that player's steps.
- [Must] When an admin runs `/skygrid prestige reset confirm`, the system shall reset every player's current step in the active season and keep the all-time highest record.

### I5: Ethical engagement review gate
As a **player**, I want every challenge/season/progression mechanic vetted against dark-pattern
risks before release, so that the game stays fun rather than manipulative.
- Every new mechanic passes the checklist in `.claude/skills/challenge-season-system/SKILL.md`,
  reviewed by `player-psychology-designer`, before implementation.

## Epic J: Economy and marketplace

Roles as above, plus **player** and **plugin developer** (a third-party plugin that consumes Vault).
The design is in `docs/design/economy.md`. Coins are whole (0 fractional digits, fixed for go-live)
and are never sold for real money by the server.

### J1: Balance and payments
As a **player**, I want to see my coin balance and pay other players, so that I know what I can afford and can trade outside the market.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Must] The system shall store each balance as a non-negative whole number of coins per season.
- [Must] When a player joins for the first time in a season, the system shall create the account with the configured starting balance (default 0) as one audited ledger transaction.
- [Must] When a player runs `/balance` (alias `/bal`), the system shall show the balance with the configured currency name, and with `skygridng.economy.balance.others`, another player's balance.
- [Must] When a player runs `/pay <player> <amount>`, the system shall debit the payer and credit the recipient in one transaction, or change neither.
- [Must] If the amount is zero, negative, has a fractional part, exceeds `economy.pay.maxAmount` or the payer's balance, or would push the recipient above `economy.maxBalance`, then the system shall reject the payment and echo the value and the balance.
- [Must] While the payer's pay cooldown (2 s by default) runs, the system shall reject another `/pay` and show the remaining time.
- [Must] If the database is unreachable, then the system shall reply with an "economy unavailable" message and never show a cached value as current.

### J1c: Audited admin adjustments
As a **server admin**, I want to give, take and set balances with a full audit trail, so that I can fix mistakes without losing track of where coins came from.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Must] When an admin with `skygridng.economy.admin` runs `/skygrid eco give|take <player> <amount> [reason]`, the system shall post one ledger transaction recording the actor and the reason.
- [Must] If `take` exceeds the balance, then the system shall reject it and echo the balance, without clamping.
- [Must] When an admin runs `/skygrid eco set <player> <amount>`, the system shall require `confirm` and post the difference as a delta.
- [Must] When an admin runs `/skygrid eco history <player> [page]`, the system shall list that player's ledger entries newest first.
- [Must] The system shall never update or delete a ledger row; corrections are new, opposite transactions.

### J1d: Currency task reward
As a **server admin**, I want a task to reward coins, so that challenges feed the economy.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Must] The system shall accept `type: currency` with a decimal `amount` as a task reward in `challenges/<id>.<ext>` and in the generated challenge schema.
- [Must] When a new task completion carries a currency reward, the system shall credit the player under a season-scoped idempotency key, also while the player is offline.
- [Must] If a completion is recorded but its credit is missing (a crash between the two commits), then the system shall apply the credit on the player's next join.

### J1e: Vault economy provider
As a **plugin developer**, I want SkyGrid's currency behind the Vault API, so that existing shop, rank and placeholder plugins work with it unchanged.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Must] Where Vault or VaultUnlocked is installed, the system shall register SkyGrid's economy with it at the configured priority (default `Normal`), and log a warning naming any competing provider.
- [Must] Where neither is installed, the system shall start normally and keep every other economy feature working.
- [Must] When a Vault call passes a negative, NaN, infinite or fractional amount, the system shall return a failure response and change nothing.
- [Must] Every successful Vault deposit or withdrawal shall be one ledger transaction, tagged with the calling plugin's name where the API provides it.

### J1f: Admin shop
As a **player**, I want to buy and sell items at fixed prices in a shop GUI, so that I can turn SkyGrid loot into coins and coins into blocks I lack.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Must] When a player runs `/shop [id]`, the system shall show a chest GUI with each entry's item, quantity, buy price, sell price and the player's balance, loaded from `shops/<id>.<ext>`.
- [Must] When a player buys an entry, the system shall debit the price and queue the item to the mailbox in one transaction.
- [Must] When a player sells an entry, the system shall remove exactly the configured quantity of matching, unmodified items and credit the sell price, in a crash-safe way.
- [Must] If a shop sells an item above the lowest buy price of the same item in any shop, then the system shall reject the shop file on load, naming both entries.
- [Must] While a player is in creative or spectator mode, the system shall refuse shop sales.

### J1g: Player marketplace with item escrow
As a **player**, I want to list items for a price and buy other players' listings, so that a player-driven market forms.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Must] When a player runs `/market sell <price>`, the system shall move the held stack into escrow and create an active listing for the configured duration (48 h by default).
- [Must] When a player buys a listing, the system shall in one transaction verify that it is still active, debit the buyer, credit the seller minus the 5% market tax, credit the tax to the market tax sink, mark the listing sold, and queue the item to the buyer's mailbox.
- [Must] If two players buy the same listing concurrently, then exactly one purchase shall succeed and the other shall get an "already sold" message with no balance change.
- [Must] If the player already has 10 active listings, the price is outside the configured range, or the item exceeds the size cap, then the system shall reject the listing and keep the item in hand.
- [Must] When a seller cancels a listing or it expires, the system shall queue the item to the seller's mailbox.
- [Must] The system shall not let a player buy their own listing, and the market GUI shall show the price, the tax and the seller's net before the seller confirms.

### J1h: Mailbox for undelivered items
As a **player**, I want items I bought, or listings that came back, to wait for me, so that nothing is lost while I am offline or my inventory is full.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Must] When a delivery is queued for an online player with enough inventory space, the system shall deliver it immediately.
- [Must] If the inventory has no room for the whole stack, then the system shall keep the item in the mailbox and tell the player to run `/market claim`.
- [Must] When a player joins, the system shall reconcile pending deliveries and pending escrows before delivering anything new.
- [Must] When a season ends, the system shall expire mailbox contents with the season, under the same inventory policy as the player's inventory.

### J1i: Ledger integrity check
As a **server owner**, I want to verify that no coins were created or lost, so that I can trust the economy after a crash.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Should] When an admin runs `/skygrid eco audit`, the system shall report whether the postings of every transaction sum to zero and every balance equals the sum of its postings, naming each mismatch.

### J1j: Market browsing
As a **player**, I want to find listings quickly, so that I do not have to scroll through the whole market.
- Status: Specified, not implemented (`docs/design/economy.md`).
- [Should] The market GUI shall page listings newest first and filter them by item id with `/market search <item>`.
- [Could] The market GUI shall sort listings by unit price.

### J1k: Balance leaderboard
- Status: Deferred (`docs/design/economy.md`, section 3.2 item 3).
- [Won't] A balance leaderboard in this release, until the I5 review of social comparison has been done.


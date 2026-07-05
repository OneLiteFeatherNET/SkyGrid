---
name: challenge-season-system
description: >-
  Explains SkyGrid-ng's Challenge and Season system (Epic I in docs/USERSTORIES.md): the domain
  shape (ChallengeRule/ChallengeCatalog today; ChallengeRuleSet/Season/player-progress as
  documented future extensions), why it is an OLF-only mechanic living in adapters/challenge and
  the platforms.olf package inside platforms/common, and the reusable engagement-research
  methodology (an 8-point dark-pattern
  checklist, a structural-precedent checklist, and a fixed source list) that must be re-applied
  before designing any new challenge, season, or progression mechanic. Use when adding or reviewing
  a challenge rule, planning a Season, designing adaptive/prestige progression, or evaluating
  whether a reward/pacing mechanic is a dark pattern. Cross-links config-schema-and-rules (the
  block-centric rule model this system deliberately does not reuse) and user-stories (Epic I/J).
---

# Challenge and Season system

Epic I in `docs/USERSTORIES.md` covers BastiGHG-style challenges (rule modifiers plus goal
tracking) delivered in fixed-duration Seasons, plus an adaptive meta-progression system. Epic J is
a rough, unscheduled sketch of a SkyBlock-style economy that shares this epic's biggest open
dependency (per-player persistence). This skill is the standing reference so that judgment already
made once (what counts as a dark pattern here, why the domain model is shaped the way it is) does
not have to be rederived for every new challenge or season.

## Why this is not an extension of the per-block rule model

See `config-schema-and-rules` for the existing `Rule` model: it resolves `BlockId -> state/
physics/placement`, one rule per block. A challenge rule is a different axis entirely:
`player/session -> allowed actions`, nothing to do with block placement. Forcing a challenge into
`RuleRepository`/`RuleDtos` would violate that model's whole premise. Challenges get their own
`api.model` types, `ports.out.ChallengeRepository`, and a `challenges/` config directory, sibling
to (not part of) `presets/`/`rules/`.

Season is likewise not "a preset with a lifecycle": a preset answers "what does the world look
like," a Season answers "what challenge rule set and goals are live for a fixed duration,"
independent of which preset a world happens to use. Presets have no start/end/rotation concept
today and none should be bolted on; a Season is its own model that can reference a preset.

## Domain shape

**Built today**: `ChallengeRule(id, description, active)` and `ChallengeCatalog` (a map of these),
loaded/saved via `ports.out.ChallengeRepository`, admin-toggled via `ports.in.ChallengeAdminUseCase`
(`list`, `isActive`, `activate`, `deactivate`). Deliberately minimal, no season window or per-world
scope yet.

**Documented future extensions, not yet built** (all blocked on a genuinely new per-player
persistence port/adapter, since none exists anywhere in `api`/`ports`/`core` today):
- `ChallengeRuleSet`: a named bundle of `ChallengeRule`s, the goal/achievement layer within it
  (Epic I2).
- `Season`: start/end window plus a bound `ChallengeRuleSet` (and optionally a preset reference),
  orthogonal to `Preset` (Epic I3).
- Per-player progress/prestige state (Epic I4), owned jointly by `core-architect` (the port/state
  shape) and `postgresql-specialist` (the adapter), once scheduled. Unlike `live-ops-analyst`'s
  aggregate analytics (which prefers pseudonymized identifiers), this needs a real, stable
  per-player identity to attach durable state to.
- A `Money`-style record for Epic J (see below), once that epic is scheduled.

## Why challenges are OLF-only

There is a single Paper distribution today (`platforms/paper-olf` was folded into `platforms/paper`
once the project narrowed to running only on OneLiteFeather's own server); "OLF-only" is now a
naming convention, not a module boundary. `platforms/common`'s own code (`OlfFeature`, `OlfModule`,
`NetworkStartupReportFeature`, all in its `platforms.olf` package) and the `hexagonal-architecture`
skill name "challenges" as the reference example of this convention. New challenge enforcement code
still lives in a dedicated adapter module (`adapters/challenge`, mirroring `adapters/spawn`'s
single-listener shape), wired via an `OlfFeature` bound in `platforms.olf.OlfModule` and always
enabled by `SkyGridNgPlugin`. The shared `ChallengeAdminUseCase` port is a plain, always-present
Guice binding now (no `OptionalBinder`), since there is no distribution where it would be absent.

## Money type for Epic J: do not use JSR 354

JSR 354 (`javax.money`/Moneta) was evaluated and rejected: the only real implementation (Moneta)
hard-requires `jakarta.annotation` and `okhttp3` at runtime, which breaks the framework-free-core
rule the same way `jakarta.inject` is already banned. Unlike Adventure (where Paper itself supplies
the runtime implementation, so a `compileOnly` dependency in core costs nothing at runtime),
nothing in Paper/Bukkit implements `javax.money`. If Epic J is ever scheduled, use a small
`Money`-style immutable record in `api.model` instead, following the exact pattern `BlockId`
already established: framework-free, validated in a compact constructor.

## The reusable engagement-research methodology

Apply this every time a new challenge, season, or progression mechanic is proposed, not just once.

### Step 1: frame the mechanic
Write one sentence describing what it does and which player behavior it's meant to encourage.

### Step 2: the 8-point dark-pattern checklist
1. Which Octalysis core drive(s) does this engage? If unpredictability or loss/avoidance is
   present, can it be restated using only the other drives without losing its appeal? Prefer the
   restatement if yes.
2. Does the mechanic still work under full transparency about odds, timing, and true cost? If it
   only works because the player misjudges these, reject it.
3. Which Bartle type(s) does it serve, and does the season as a whole still serve the other three?
4. Does reward rate/magnitude adapt to a struggle/disengagement signal, or only to a demonstrated
   skill/achievement signal? The former is the **compensation-loop risk** and needs explicit
   justification; adapting *difficulty/pacing* to behavior (flow theory, precedented by Left 4
   Dead's AI Director) is the lower-risk alternative and should be preferred.
5. Can a late-joining or irregular-session player still have a complete, satisfying experience of
   what they did play, with nothing retroactively taken away? (Hypixel SkyBlock Bingo's fresh,
   disposable per-event profile is the strongest precedent: everyone starts from zero when the
   event starts, so joining late costs time, not relative standing, rather than needing a
   catch-up multiplier bolted onto persistent progress.)
6. Would a skeptical outside observer (journalist, regulator) plausibly describe this mechanic as
   manipulative in one sentence? If yes, revisit even if it "works."
7. What falsifiable metric would prove this mechanic is failing versus succeeding players, for
   `playtest-qa-coordinator` to check against?
8. Does the mechanic require an anxiety-inducing countdown the player cannot control, versus a
   purely narrative/organizational timer (a season has a start/end like a sports season)? Only the
   latter is acceptable.

### Step 3: structural precedent check
Apply when the mechanic involves a new rule-modifier or goal-tracking shape:
- Vanilla Tweaks-style composability: a flat, independently toggleable modifier, or does it need
  dependency/incompatibility rules?
- Origins-style bundling: a named bundle of modifiers assigned via a layer, changeable later?
- UHC scenario tradition: an optional named modifier layered on a base ruleset, host/vote selected?
- Hypixel Bingo's season-reset model: whenever late-join fairness or grind/burnout risk is in
  play, consider a fresh, disposable, time-boxed profile/state over a catch-up multiplier.
- Note explicitly what's confirmed-live, community-sourced, or your own inference; search-tool
  reliability varies session to session, don't let a thin source get cited as settled fact.

### Step 4: fixed source list to check every time
- FTC "Bringing Dark Patterns to Light" (2022) and Gray, Kou, Battles, Hoggatt, Toombs, "The Dark
  (Patterns) Side of UX Design" (CHI 2018): the plain-language and structured dark-pattern checks.
- The loot box/variable-ratio literature (King and Delfabbro; Belgian Gaming Commission 2018; UK
  DCMS loot box review) whenever any randomized reward is involved, cosmetic or not.
- Kahneman and Tversky's prospect theory (loss aversion) whenever a mechanic can be described as
  "you might lose X."
- Csikszentmihalyi's flow channel and the Left 4 Dead AI Director model as the reference point for
  any adaptive-difficulty proposal.
- Verify before quoting verbatim, confidence was flagged moderate on first pass: Mathur, Mayer,
  Kshirsagar (CHI 2021); Zagal, Björk, Lewis (DiGRA 2013); Hunicke's 2005 ACE paper; Andrade et
  al.'s RL/player-modeling DDA work.
- No confirmed IGDA monetization/engagement ethics document exists; do not cite one without
  checking igda.org directly first.
- This project's own `docs/USERSTORIES.md` Epic I and any prior `playtest-qa-coordinator` findings,
  to avoid re-litigating settled ground.

### Step 5: record the answers
Write step 2-4's answers into the epic's planning artifact for that mechanic (not the story itself,
that comes at plan approval) so the next pass starts from written precedent, not institutional
memory alone.

## Epic lettering convention

`docs/TEAM.md` reserves epic letters ahead of `docs/USERSTORIES.md` catching up: F (player
engagement hypotheses), G (accessibility), H (live-ops) were reserved but unwritten when this
system was designed. Challenges and Seasons is Epic I; the economy sketch is Epic J. Check
`docs/TEAM.md` for any letters reserved after J before assigning a new one.

## Where to look

- The exact domain model built so far: `api/src/main/java/net/onelitefeather/skygridng/api/model/
  ChallengeRule.java`, `ChallengeCatalog.java`.
- The ports: `ports/src/main/java/net/onelitefeather/skygridng/ports/out/ChallengeRepository.java`,
  `ports/src/main/java/net/onelitefeather/skygridng/ports/in/ChallengeAdminUseCase.java`.
- The OLF-only wiring seam: `platforms/common/src/main/java/net/onelitefeather/skygridng/
  platforms/olf/{OlfFeature,OlfModule,ChallengeModule,ChallengeFeature}.java`.
- The config layout precedent this deliberately does not reuse: `config-schema-and-rules` skill.
- The full requirements and acceptance criteria: `docs/USERSTORIES.md` Epic I and Epic J.

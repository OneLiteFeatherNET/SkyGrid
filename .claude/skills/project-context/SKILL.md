---
name: project-context
description: >
  Use whenever you need the why behind SkyGrid-ng: its history and evolution, the key design
  decisions and their rationale, the GoF patterns in use, the determinism guarantees, the
  OneLiteFeather open source standards, the maintenance roadmap, and the hard constraints. Read this
  before making a structural, dependency, licensing, naming, or process change so you do not
  accidentally undo a deliberate decision. It captures institutional memory that is not obvious from
  the code alone.
---

# SkyGrid-ng project context

The institutional memory: why the project is the way it is. For code structure see
`hexagonal-architecture`, for the build and release process see `build-and-release`, for the long
term plan see `docs/MAINTENANCE.md`. The detailed, dated decision log is in
`references/decision-log.md`; read it before reversing anything that looks deliberate.

## History (how it got here)

1. Started as a Kotlin Wave Function Collapse mockup for a Paper chunk generator.
2. Rewritten as a pure Java 25, hexagonal architecture WFC prototype with JSON rules and an in game
   admin command.
3. Reframed around reviving SkyGrid: added internationalization via Adventure, made the whole code
   base English, and added two fast seed based algorithms (`skygrid`, `noise_skygrid`) switchable by
   config alongside the retained `wfc`.
4. Open sourced under OneLiteFeather: AGPL, release-please, Conventional Commit history, Guice DI,
   reusable org workflows, full docs.
5. Renamed from GridWFC to SkyGrid-ng (full package and identifier rebrand). The WFC algorithm names
   were intentionally kept (see below).
6. Added a comprehensive JUnit 5 black-box and white-box test suite from core to plugin.

## Key decisions and rationale (do not undo without reason)

- Paper 26.1 as the target: the first fully deobfuscated Paper era, which reduces breakage and makes
  the adapter layer stabler.
- Java 25: records for immutable domain values, modern language features, a clean core.
- Hexagonal architecture: the generation core depends on nothing, so it is testable without a server
  and survives Paper API churn. This is the load bearing decision; protect it.
- Three algorithms, not one: `wfc` was kept for coherent surface terrain; `skygrid` and
  `noise_skygrid` were added as O(1), seed based, parallel safe alternatives. Algorithms sit behind
  one port (`TerrainAlgorithm`) so a fourth can be added without touching the generator.
- Adventure based i18n: the core stays text free; the command adapter renders
  `Component.translatable`, and Paper renders per client locale. English and German ship.
- English everywhere in code; user facing text only in translation bundles.
- Guice DI with the composition root in `bootstrap`: the core carries no injection annotations, so it
  stays framework free while wiring is centralized.
- AGPL-3.0 for the plugin (OneLiteFeather libraries use Apache-2.0).
- 64 tile WFC limit: the solver domain is a 64 bit mask, so a region may have at most 64 tile types.
- Naming after the rename: project branded names are SkyGrid-ng (`SkyGridNgPlugin`, `/skygrid`,
  permission `skygridng.admin`, i18n namespace `skygridng`). The remaining WFC names (`WfcSolver`,
  `WfcSurfaceAlgorithm`, algorithm id `wfc`) describe the Wave Function Collapse method, not the
  project, and stay.

## Design patterns (GoF) in use

Strategy (`EntropyHeuristic`, `CollapseStrategy`, `TerrainAlgorithm`), Factory (`AlgorithmService`,
`JsonRuleRepository`), Repository (`RuleRepository`), Flyweight (shared immutable `TileType`,
`BlockWeight`), Observer (`RuleState` listeners for cache invalidation and algorithm rebuild),
Memento (immutable `GeneratorConfig` plus undo stack), Command (`SkyGridCommand` subcommands).

## Determinism guarantee

Same world seed and coordinates always yield the same result. Seeds are derived reproducibly with
SplitMix64 in `Seeds`. WFC region grid lines are solved first as 1D WFC with anchor endpoints, then
2D region interiors are solved against those fixed borders, so seams are always rule conformant
(verified: zero adjacency violations over thousands of edges, zero determinism deviations). Preserve
this when changing generation.

## OneLiteFeather standards

AGPL plugins (Apache libs); release-please driven releases; Conventional Commits; Renovate org
presets; Crowdin for translations; CODEOWNERS teams `skygrid-ng-maintainers` and `core-team`;
reusable workflows from `OneLiteFeatherNET/workflows`; CycloneDX SBOM into Dependency-Track. The
guiding maintenance principle is automation first: humans review and decide, machines build, release,
and update. See `docs/MAINTENANCE.md` for the five year plan and version policy.

## Hard constraints (always)

- Never use em dashes anywhere (code, docs, commits). Use a comma, colon, parentheses, or period.
- English for all identifiers, comments, docs, and commit messages.
- Conventional Commits; never edit the version in `gradle.properties` by hand (release-please owns
  it).
- Keep the core free of Bukkit, Gson, Guice, jakarta.inject, and Adventure.

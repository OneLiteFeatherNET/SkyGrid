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
- Three algorithms, not one (amended, later removed to two): `wfc` was kept for coherent surface
  terrain; `skygrid` and `noise_skygrid` were added as O(1), seed based, parallel safe alternatives.
  Algorithms sit behind one port (`TerrainAlgorithm`) so another can be added without touching the
  generator. `wfc` was later removed entirely: its solid-terrain surface area (a dedicated solver
  package, a mandatory config section, and public API/`RuleAdminUseCase` tile-admin methods) cost
  more to maintain than the two void-grid algorithms needed, and no shipped preset used it as its
  primary algorithm. See `references/decision-log.md` for the dated entry.
- Adventure based i18n: the command adapter renders `Component.translatable`, and the core may build
  Adventure text too (for example `SkyGridApi.describe()`); Paper renders per client locale. English
  and German ship.
- English everywhere in code; user facing text only in translation bundles.
- Guice DI with the composition root in `bootstrap`: the core carries no injection annotations, so it
  stays framework free while wiring is centralized.
- AGPL-3.0 for the plugin (OneLiteFeather libraries use Apache-2.0).
- 64 tile WFC limit (historical, no longer applicable): while `wfc` existed, its solver domain was a
  64 bit mask, so a region could have at most 64 tile types. Removed along with `wfc`.
- Naming after the rename: project branded names are SkyGrid-ng (`SkyGridNgPlugin`, `/skygrid`,
  permission `skygridng.admin`, i18n namespace `skygridng`). The WFC names (`WfcSolver`,
  `WfcSurfaceAlgorithm`, algorithm id `wfc`) described the Wave Function Collapse method, not the
  project; they were removed with the algorithm rather than kept as dead branding.

## Design patterns (GoF) in use

Strategy (`TerrainAlgorithm`), Factory (`AlgorithmFactory`, the config repositories), Repository
(`RuleRepository`, `PresetRepository`), Flyweight (shared immutable `BlockWeight`), Observer
(`RuleState` listeners for cache invalidation and algorithm rebuild), Memento (immutable
`GeneratorConfig` plus undo stack), Command (Incendo Cloud `@Command` handlers in
`adapters.command.handler`). The Wave Function
Collapse era added a second Strategy pair (`EntropyHeuristic`, `CollapseStrategy`) and a Flyweight
(`TileType`); both were removed with the `wfc` algorithm.

## Determinism guarantee

Same world seed and coordinates always yield the same result. Seeds are derived reproducibly with
SplitMix64 in `Seeds`. Preserve this when changing generation. (While `wfc` existed, its region grid
lines were solved first as 1D WFC with anchor endpoints, then 2D region interiors were solved against
those fixed borders, so seams were always rule conformant; that machinery was removed with `wfc`.)

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
- Keep the core free of Bukkit, Gson, Guice, and jakarta.inject. Adventure text
  (`net.kyori.adventure`) is allowed as a platform-neutral API; its platform bindings
  (`net.kyori.adventure.platform`) are not.

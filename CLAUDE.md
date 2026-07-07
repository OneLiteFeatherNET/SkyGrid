# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# SkyGrid-ng

A Paper 26.1 chunk generator plugin in pure Java 25 with a hexagonal architecture. It ships two
generation algorithms (`skygrid`, `noise_skygrid`), a JSON config, an in game `/skygrid`
admin command, and i18n. Maintained by OneLiteFeather (OLF).

This file is the always on context for every session. The deeper, task specific knowledge lives in
the skills under `.claude/skills/`. Read the matching skill before doing the work.

## Standard workflow

Every prompt in this repository, without exception for size or triviality, runs through the nine
phase chain-of-thought in `.claude/skills/standard-workflow/SKILL.md`: check spelling/clarity,
derive requirements, get requirements approved, survey the current repo state, research external
sources (web, DeepWiki, Context7), draft an implementation plan, get the plan approved, implement
with feedback folded in, test (deciding per change whether ArchUnit coverage is warranted), and
close with a result summary. Read that skill before responding to any prompt, not just large ones.
Delegated work (investigation, research, implementation, tests) always goes through the `producer`
agent first; it is the standard entry point for the specialist team, not a fallback.

## Golden rules

- English everywhere in the repository: code, identifiers, comments, docs, and commit messages.
- Never use em dashes anywhere (not in code, docs, or commits). Use a comma, colon, parentheses,
  or a period instead.
- The dependency direction is one way: `platforms -> adapters -> ports -> api`, and `core -> ports
  -> api`. The `api`, `ports`, and `core` modules must never import Bukkit, Jackson, Guice, or
  jakarta.inject; the adapters must never import `core`. Adventure (`net.kyori.adventure`) is allowed
  in `api`/`ports`/`core` as a platform-neutral text API (it is `compileOnly`; the platform provides
  it at runtime), but its platform-specific bindings (`net.kyori.adventure.platform`) are not. If an
  edit would break either, the design is wrong. See the `hexagonal-architecture` skill.
- Keep value types immutable (records), validate in compact constructors, and keep generation
  deterministic from the world seed.
- The core is platform-neutral, not just Bukkit-import-free: name blocks by namespaced `BlockId`
  (`minecraft:stone`), never a Bukkit `Material` enum name, in `api`/`ports`/`core` or the config files.
  Resolve to a native block only in the adapter (WorldEdit pattern; see `hexagonal-architecture`).

## Architecture map

Multi-module Gradle build grouped under category folders (CloudNet-style). Packages all share the
root `net.onelitefeather.skygridng`. Modules and the one-way dependency arrows:

- `api/` (`api.*`, framework free): the public contract. `api.model` (immutable records),
  `api.SkyGridApi` (read-only facade), `api.util.Seeds` (deterministic seed derivation). No deps.
- `ports/` (`ports.*`, framework free): the interfaces adapters compile against. `ports.in`
  (`GenerateRegionUseCase`, `RuleAdminUseCase`, `TerrainAlgorithmProvider`), `ports.out`
  (`PresetRepository`, `RuleRepository`), `ports.algorithm` (`TerrainAlgorithm`, `ColumnSink`).
  Depends on `api`.
- `core/` (`core.*`, framework free): the domain. `algorithm` (the two terrain algorithm impls),
  `service` (use case impls, incl. `SkyGridApiService`),
  `state` (`RuleState`), `util` (`ValueNoise`, `WeightedTable`). Implements the ports; depends on
  `ports` + `api`.
- `adapters/generator` (`adapters.generator`): framework-free, like `core`, depending on `ports` +
  `api` only. Holds `DecorationEngine` (the per-block decoration/chest/spawner pass), `StructurePlacer`,
  and `StillWater`, all driven through the new `ports.algorithm.RegionWriter` port instead of a native
  Bukkit type, so this module never imports Bukkit, Guice, or jakarta.inject.
- `adapters/spawn` (`adapters.spawn`): framework-free, like `core`, depending on `ports` + `api`
  only. Holds `SafeLocationFinder` (the first-join safe-spawn search), driven through the new
  `ports.algorithm.GroundReader` port instead of a native Bukkit type, so this module never imports
  Bukkit, Guice, or jakarta.inject.
- `adapters/command` (`adapters.command`): framework-free, like `core`/`adapters/generator`/
  `adapters/spawn`, depending on `ports` + `api` only. Holds `SkyGridCommands` (the Cloud-annotated
  `/skygrid` subcommands), its `parser.SkyGridArgumentParsers` (the tab-completion suggestion
  providers), and `Messages` (Adventure), driven entirely through Cloud's core/annotations API
  instead of a native Bukkit sender, so this module never imports Bukkit, Cloud's Paper/Brigadier
  bindings, Guice, or jakarta.inject; the Paper/Cloud-paper binding of the command manager lives in
  `platforms/common`.
- `adapters/config` (`adapters.config`): now the one remaining adapter with a framework dependency,
  and it is Jackson (a cross-platform serialization library), never Bukkit, so it resolves to
  nothing Paper-specific, unlike the Bukkit-facing code the sibling adapters above held before
  framework-free cleanup. Depends on `ports` + `api` only, never on `core`. Holds
  `PresetRepository` + `RuleRepository`: the split `config` selector, one file per preset under
  `presets/` and one per block under `rules/`, in JSON/YAML/TOML, plus the victools-generated
  editor `schema/`.
- `adapters/challenge` (`adapters.challenge`): framework-free, like `core`/`adapters/generator`/
  `adapters/spawn`/`adapters/command`, depending on `ports` + `api` only. Holds only
  `ChallengeBehavior` (a plain marker interface); its four concrete implementations live in
  `platforms/common`'s `platforms.olf.challenge` package instead.
- `platforms/common` (`platforms.common.*`): the shared Bukkit composition root. `SkyGridNgPlugin`,
  `I18n`, the Guice modules (`PlatformModule`, `RepositoryModule`, `GeneratorModule`), the message
  bundles, `platforms.common.generator` (the Bukkit implementation of the generator: `HexagonalChunkGenerator`,
  `RuleResolver`, `MaterialResolver`, `BukkitIds`, `BiomeResolver`, `SkyGridBiomeProvider`,
  `LimitedRegionWriter` implementing `RegionWriter` over a live `LimitedRegion`, and the thin
  `SkyGridDecorationPopulator` shell that delegates to `adapters.generator.DecorationEngine`),
  `platforms.common.spawn` (the Bukkit implementation of the spawn search: `SpreadSpawnListener` and
  `BukkitGroundReader` implementing `GroundReader` over a live `World`),
  `platforms.common.command` (`PaperCommandSource`, `CloudCommandManagerFactory`, and
  `CloudCommandRegistrar`: the Paper/Cloud-paper binding of the `/skygrid` command manager, and the
  seam that parses `adapters.command.SkyGridCommands` onto it), and
  `platforms.olf.*` (`OlfModule`, `OlfFeature`, `ChallengeModule`, `ChallengeFeature`, and
  `platforms.olf.challenge` with the four `ChallengeBehavior` implementations: `OneLifeListener`,
  `NoNaturalRegenerationListener`, `NoCraftingTableListener`, `LevelBorderWarningTask`):
  a naming convention, not a module boundary, for mechanics specific to this OneLiteFeather (OLF)
  server (challenges) rather than general-purpose plugin behavior. `SkyGridNgPlugin.modules()` and
  `onExtensionEnable()`/`onExtensionDisable()` always wire and enable every bound `OlfFeature`, there
  is no distribution without them. Exposes the whole graph; the only place that may use Bukkit,
  Guice, and jakarta.inject (Adventure text is allowed in every layer). A library (no
  shadow/plugin-yml).
- `platforms/paper` (no package, thin): the single Paper distribution. plugin.yml + shadow +
  run-paper + publishing. Produces `skygrid-ng-<version>.jar`. Holds the bootstrap integration tests.

- `verification/`: ArchUnit tests (no production code) that enforce the dependency rule and the
  platform-neutral core at build time. Update `ArchitectureTest` if you add a layer.

`api`/`ports`/`core` carry no injection annotations; swapping an adapter changes a module binding,
not the core. `SkyGridNgPlugin` is extensible via `modules()`, `injector()`, and
`onExtensionEnable()`/`onExtensionDisable()`. Shared build config lives in the
`skygrid.java-conventions` plugin in `buildSrc`. The architecture rules are enforced by ArchUnit in
`:verification`.

Plugin lifecycle: `onLoad` builds the whole Guice graph eagerly (`Stage.PRODUCTION`, which loads
config), `onEnable` registers i18n, the `SkyGridApi` Bukkit service, and the command;
`getDefaultWorldGenerator` hands Paper the `HexagonalChunkGenerator`. The plugin must load at server
`STARTUP` so the generator exists before worlds load. The active algorithm is read from `RuleState`
and is switchable at runtime via `/skygrid algorithm <id>`.

## Where to look

- Any prompt at all, before doing anything else: read `.claude/skills/standard-workflow/SKILL.md`.
- Changing or adding Java code (core, ports, adapters, services, generator, command, repository,
  Guice modules): read `.claude/skills/hexagonal-architecture/SKILL.md`.
- Writing or changing tests: read `.claude/skills/testing/SKILL.md`.
- Changing the Gradle build, dependencies, plugin-yml, publishing, CI, or cutting a release:
  read `.claude/skills/build-and-release/SKILL.md`.
- Working on what the plugin generates or its config and gameplay behavior:
  read `.claude/skills/skygrid-domain/SKILL.md`.
- Editing the config files, presets/ or rules/ layout, the JSON/YAML/TOML formats, the JSON Schemas,
  or the per-block state/physics/placement rules: read `.claude/skills/config-schema-and-rules/SKILL.md`.
- Planning or reviewing a feature against its requirements:
  read `.claude/skills/user-stories/SKILL.md`.
- Needing the why behind a structural, dependency, licensing, naming, or process choice (so you do
  not undo a deliberate decision): read `.claude/skills/project-context/SKILL.md`.

## Common commands

- Build (all modules): `./gradlew build`
- Test: `./gradlew test` (JUnit 5 + AssertJ + Mockito + MockBukkit v4); one module: `./gradlew :core:test`
- Single test: `./gradlew :core:test --tests "net.onelitefeather.skygridng.core.algorithm.SkyGridAlgorithmsTest"`
  (append `.methodName` to run one method)
- Run a test server: `./gradlew :platforms:paper:runServer` (creates `run/`, which is gitignored)
- Build the shaded jar: `./gradlew :platforms:paper:shadowJar` (output
  `platforms/paper/build/libs/skygrid-ng-<version>.jar`)
- Fast core check: `.claude/skills/hexagonal-architecture/scripts/compile-core.sh` (javac on api + ports + core)

## Commits and versioning

Use Conventional Commits (`feat`, `fix`, `refactor`, `test`, `build`, `chore`, `docs`, `ci`).
release-please reads the history to bump the version in `gradle.properties` and write the
changelog. A breaking change uses `!` (for example `refactor!:`). Do not edit the version by hand.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

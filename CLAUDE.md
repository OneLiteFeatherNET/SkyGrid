# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# SkyGrid-ng

A Paper 26.1 chunk generator plugin in pure Java 25 with a hexagonal architecture. It ships three
generation algorithms (`skygrid`, `noise_skygrid`, `wfc`), a JSON config, an in game `/skygrid`
admin command, and i18n. Maintained by OneLiteFeather (OLF).

This file is the always on context for every session. The deeper, task specific knowledge lives in
the skills under `.claude/skills/`. Read the matching skill before doing the work.

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
- `core/` (`core.*`, framework free): the domain. `algorithm` (the three terrain algorithm impls),
  `solver` (the WFC solver and strategies), `service` (use case impls, incl. `SkyGridApiService`),
  `state` (`RuleState`), `util` (`ValueNoise`, `WeightedTable`). Implements the ports; depends on
  `ports` + `api`.
- `adapters/generator`, `adapters/command`, `adapters/config`, `adapters/spawn` (`adapters.*`): the
  Bukkit/Jackson edges. Each depends on `ports` + `api` only, never on `core`. `generator`
  (`HexagonalChunkGenerator`, material and per-block rule resolution to `BlockData`), `command`
  (`/skygrid` + Adventure messages), `config` (Jackson `PresetRepository` + `RuleRepository`: the split
  `config` selector, one file per preset under `presets/` and one per block under `rules/`, in
  JSON/YAML/TOML, plus the victools-generated editor `schema/`), `spawn` (the spread first-join
  listener).
- `platforms/common` (`platforms.common.*`): the shared Bukkit composition root. `SkyGridNgPlugin`,
  `I18n`, the Guice modules (`PlatformModule`, `RepositoryModule`, `GeneratorModule`), and the message
  bundles. Exposes the whole graph; the only place that may use Bukkit, Guice, and jakarta.inject
  (Adventure text is allowed in every layer). A library (no shadow/plugin-yml).
- `platforms/paper` (no package, thin): the public Paper distribution. plugin.yml + shadow +
  run-paper + publishing. Produces `skygrid-ng-<version>.jar`. Holds the bootstrap integration tests.
- `platforms/paper-olf` (`platforms.olf.*`): the OneLiteFeather network distribution, a sibling of
  `platforms/paper` (both build on `platforms/common`). `OlfNetworkPlugin` extends `SkyGridNgPlugin`,
  adds `OlfModule`, enables every bound `OlfFeature` (the seam for OLF-only mechanics like
  challenges). Not published, so these never reach Modrinth. Produces `skygrid-ng-olf-<version>.jar`.

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
- Single test: `./gradlew :core:test --tests "net.onelitefeather.skygridng.core.solver.WfcSolverTest"`
  (append `.methodName` to run one method)
- Run a test server: `./gradlew :platforms:paper:runServer` (creates `run/`, which is gitignored)
- Build the shaded jar: `./gradlew :platforms:paper:shadowJar` (output
  `platforms/paper/build/libs/skygrid-ng-<version>.jar`; the OLF jar is `:platforms:paper-olf:shadowJar`)
- Fast core check: `.claude/skills/hexagonal-architecture/scripts/compile-core.sh` (javac on api + ports + core)

## Commits and versioning

Use Conventional Commits (`feat`, `fix`, `refactor`, `test`, `build`, `chore`, `docs`, `ci`).
release-please reads the history to bump the version in `gradle.properties` and write the
changelog. A breaking change uses `!` (for example `refactor!:`). Do not edit the version by hand.

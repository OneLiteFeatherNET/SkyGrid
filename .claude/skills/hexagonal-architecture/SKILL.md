---
name: hexagonal-architecture
description: >
  Use whenever editing, extending, or reviewing SkyGrid-ng Java code: the core domain, ports,
  adapters, services, the Paper chunk generator, the /skygrid command, the JSON repository, or
  the Guice composition root. Enforces the hexagonal dependency rule (adapter -> port -> core),
  keeps the core free of Bukkit, Gson, Guice, jakarta.inject, and Adventure, and provides the
  recipes for adding a generation algorithm, a command subcommand, or an out adapter. Read this
  before touching any Java file so a change does not leak framework code into the core or wire a
  dependency the wrong way.
---

# Hexagonal architecture for SkyGrid-ng

The whole point of this layout is that the generation logic is testable without a server and that
swapping an adapter (JSON for a database, Bukkit for another platform) never touches the core. Keep
that property intact.

## The one rule

Dependencies point inward only: `adapter -> port -> core`. Never the other way.

- `core` is pure Java. It must not import `org.bukkit.*`, `com.google.gson.*`, `com.google.inject.*`,
  `jakarta.inject.*`, or `net.kyori.*`. If you are about to add one of those imports to a class
  under `core`, stop: the logic belongs in an adapter, or the port is missing a method.
- Adapters depend on ports (interfaces), never on service implementations directly.
- The composition root (`bootstrap`) is the only place that knows every concrete class.

## Package map (root `net.onelitefeather.skygridng`)

- `core/model` value records: `TileType`, `FeatureSpec`, `GenerationSettings`, `GeneratorConfig`,
  `RuleSet`, `SkyGridConfig`, `BlockWeight`, `AllowedPair`, `Direction`, `AdjacencyRules`.
- `core/util`: `Seeds` (SplitMix64 derivation), `WeightedTable`, `ValueNoise`.
- `core/solver`: `EntropyHeuristic`, `CollapseStrategy` (strategy interfaces), `ShannonEntropyHeuristic`,
  `WeightedRandomCollapse`, `WfcSolver`.
- `core/algorithm`: `TerrainAlgorithm` + `ColumnSink` (the only seam the adapter implements),
  `IndependentSkyGridAlgorithm` (id `skygrid`), `NoiseSkyGridAlgorithm` (id `noise_skygrid`),
  `WfcSurfaceAlgorithm` (id `wfc`).
- `core/state/RuleState`: thread safe holder, Memento (undo) plus Observer (listeners).
- `core/port/in`: `GenerateRegionUseCase`, `RuleAdminUseCase`, `TerrainAlgorithmProvider`.
- `core/port/out`: `RuleRepository`.
- `core/service`: `RegionGenerationService`, `AlgorithmService`, `RuleAdminService` (the only
  implementors of the in ports).
- `adapter/in/paper`: `HexagonalChunkGenerator`, `MaterialPopulator`, `MaterialResolver` (package
  private). The Bukkit edge.
- `adapter/in/command`: `SkyGridCommand`, `Messages` (translatable components).
- `adapter/out/json`: `JsonRuleRepository`, `GeneratorConfigDto` (Gson edge).
- `bootstrap`: `SkyGridNgPlugin` (composition root), `I18n`, and `injection/` modules
  (`PlatformModule`, `RepositoryModule`, `GeneratorModule`).

For exact constructor and method signatures, read `references/api-surface.md` before editing. Do not
guess signatures; the records have validating compact constructors that throw
`IllegalArgumentException`, and several methods have non obvious contracts (for example
`RuleSet.tileIndex` throws on an unknown id while `tileById` returns `Optional.empty`).

## Naming

The project was renamed from GridWFC to SkyGrid-ng. Project branded names use SkyGrid-ng
(`SkyGridNgPlugin`, `/skygrid`, permission `skygridng.admin`, i18n namespace `skygridng`). The WFC
names that remain (`WfcSolver`, `WfcSurfaceAlgorithm`, algorithm id `wfc`) describe the Wave Function
Collapse method, not the project, and must stay. Do not rename them.

## Adding things

The common extensions (a new algorithm, a new command subcommand, a new out adapter) each follow a
fixed recipe. Read `references/extending.md` and follow it step by step. The short version:

- New algorithm: add one class in `core/algorithm` implementing `TerrainAlgorithm`, add its id to
  `AlgorithmService` (factory plus `isKnown`) and to the command tab completion. The Paper generator
  never changes.
- New out adapter: implement the existing `core/port/out` interface, bind it in `RepositoryModule`.
  The core does not change.

## Verifying a core change compiles

The pure core compiles with plain `javac`, no Gradle or network needed. Run
`scripts/compile-core.sh` after a core edit for a fast sanity check before the full build.

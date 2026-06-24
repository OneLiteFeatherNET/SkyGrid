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

Dependencies point inward only: `platforms -> adapters -> ports -> api`, and `core -> ports -> api`.
Never the other way.

- `api`, `ports`, and `core` are pure Java. They must not import `org.bukkit.*`, `com.google.gson.*`,
  `com.google.inject.*`, `jakarta.inject.*`, or `net.kyori.*`. If you are about to add one of those,
  stop: the logic belongs in an adapter, or a port is missing a method.
- Adapters depend on `ports` (interfaces) and `api` (model) only, NEVER on `core`. The build enforces
  this: a `core` import will not resolve in an adapter module.
- The composition root (`platforms/common`) is the only place that knows every concrete class.

## Enforcement (ArchUnit)

The rules below are not just convention. The `:verification` module runs ArchUnit tests
(`ArchitectureTest`) on every build that assert: (1) `api`/`ports`/`core` depend on no
platform/framework package (`org.bukkit`, `io.papermc`, `net.minecraft`, Gson, Guice,
jakarta.inject, Adventure); (2) adapters never depend on `core`; (3) the layered direction holds. The
module graph already blocks most violations at compile time, but ArchUnit is the net that survives
build-file drift (add paper-api to the core and it compiles, but the framework-free rule fails). If
you add a legitimately new layer or package, update `ArchitectureTest` in the same change.

## Module layout

The build is multi-module, grouped under category folders. The dependency rule is enforced at the
build level by the module graph (an adapter module simply has no `core` on its classpath) and by
ArchUnit in `:verification`:

- `api` (`api.*`): the public, framework-free contract. The immutable model (`api.model`), the
  `SkyGridApi` facade, and `api.util.Seeds`. No dependencies beyond the JDK. Published.
- `ports` (`ports.*`): the interfaces adapters compile against, framework free. `ports.in`,
  `ports.out`, `ports.algorithm`. Depends on `api`.
- `core` (`core.*`): the framework-free domain (algorithms, solver, services, state). Implements the
  ports; depends on `ports` + `api` with the `api` configuration.
- `adapters/generator`, `adapters/command`, `adapters/json` (`adapters.*`): the Bukkit/Gson edges,
  one module each. Each depends on `ports` + `api` only. Bukkit and jakarta.inject are `compileOnly`
  (the platform provides them at runtime); Gson is bundled by the platform.
- `platforms/common` (`platforms.common.*`): the shared Bukkit composition root. `SkyGridNgPlugin`,
  `I18n`, the Guice modules, the message bundles. Exposes the whole graph with `api`. The only place
  that may use Bukkit, Guice, jakarta.inject, Adventure. A library (no shadow/plugin-yml).
- `platforms/paper`: the public Paper distribution (thin: plugin.yml, shadow, run-paper, publishing).
  Produces `skygrid-ng-<version>.jar`. Holds the bootstrap integration tests (they need the generated
  plugin.yml).
- `platforms/paper-olf` (`platforms.olf.*`): the OLF network distribution, a sibling of
  `platforms/paper` (both build on `platforms/common`, neither depends on the other).
  `OlfNetworkPlugin` extends `SkyGridNgPlugin`, adds `OlfModule`, enables every bound `OlfFeature`.
  Home for OLF-only mechanics (challenges, ...). Not published. Produces `skygrid-ng-olf-<version>.jar`.

`SkyGridNgPlugin` is extensible by design: `modules()` returns the base Guice modules (override and
append), `injector()` exposes the graph, and `onExtensionEnable()`/`onExtensionDisable()` run around
the base lifecycle. A distribution layers features through these seams.

Adding a new platform (Fabric, Sponge): create a new sibling under `platforms/` that depends on the
adapters (or a new platform-specific adapter set) and the core. `api`, `ports`, `core`, and the
existing adapters never change.

## Package map (root `net.onelitefeather.skygridng`)

The second package segment matches the module: `api.*`, `ports.*`, `core.*`, `adapters.*`,
`platforms.*`.

- `api.model` value records: `TileType`, `FeatureSpec`, `GenerationSettings`, `GeneratorConfig`,
  `RuleSet`, `SkyGridConfig`, `BlockWeight`, `AllowedPair`, `Direction`, `AdjacencyRules`.
- `api.SkyGridApi`: the public read-only facade, implemented by `core.service.SkyGridApiService` and
  registered as a Bukkit service in `platforms/common`. `api.util.Seeds`: SplitMix64 derivation
  (shared by core and the generator adapter).
- `ports.in`: `GenerateRegionUseCase`, `RuleAdminUseCase`, `TerrainAlgorithmProvider`.
  `ports.out`: `RuleRepository`. `ports.algorithm`: `TerrainAlgorithm` + `ColumnSink` (the seam the
  generator adapter implements).
- `core.algorithm`: `IndependentSkyGridAlgorithm` (id `skygrid`), `NoiseSkyGridAlgorithm`
  (`noise_skygrid`), `WfcSurfaceAlgorithm` (`wfc`) - the three implementations of `TerrainAlgorithm`.
- `core.solver`: `EntropyHeuristic`, `CollapseStrategy` (strategy interfaces), `ShannonEntropyHeuristic`,
  `WeightedRandomCollapse`, `WfcSolver`. `core.util`: `WeightedTable`, `ValueNoise`.
- `core.state.RuleState`: thread safe holder, Memento (undo) plus Observer (listeners).
- `core.service`: `RegionGenerationService`, `AlgorithmService`, `RuleAdminService`,
  `SkyGridApiService` (the implementors of the in ports and the facade).
- `adapters.generator`: `HexagonalChunkGenerator`, `MaterialPopulator`, `MaterialResolver`.
- `adapters.command`: `SkyGridCommand`, `Messages` (translatable components).
- `adapters.json`: `JsonRuleRepository`, `GeneratorConfigDto`, and the bundled default
  `generator.json` resource.
- `platforms.common`: `SkyGridNgPlugin` (composition root), `I18n`, the `injection/` modules
  (`PlatformModule`, `RepositoryModule`, `GeneratorModule`), and the `messages/` bundles.
- `platforms.olf`: `OlfNetworkPlugin`, `OlfModule`, `OlfFeature`, `NetworkStartupReportFeature`.

For exact constructor and method signatures, read `references/api-surface.md` before editing. Do not
guess signatures; the records have validating compact constructors that throw
`IllegalArgumentException`, and several methods have non obvious contracts (for example
`RuleSet.tileIndex` throws on an unknown id while `tileById` returns `Optional.empty`).

## Platform neutrality (no Bukkit vocabulary in the core)

The framework-free layers must be neutral semantically, not just syntactically. A missing
`org.bukkit` import is not enough if the data still speaks Bukkit. Concretely:

- A block is named by a platform-neutral, namespaced id, the `api.model.BlockId` value type
  (`namespace` + `value`, lowercased and validated; `BlockId.of("stone")` defaults to `minecraft`).
  The canonical string form is `minecraft:stone`. NEVER use a Bukkit `Material` enum name
  ("STONE", "GRASS_BLOCK") in `api`, `ports`, `core`, or `generator.json`, and never hardcode one in
  an algorithm (`WfcSurfaceAlgorithm` uses `BlockId` constants, not `"BEDROCK"`).
- The model (`BlockWeight`, `TileType`, `FeatureSpec`), the `ColumnSink` port, and the weighted table
  carry `BlockId`. Resolution of a `BlockId` to a native `org.bukkit.Material` happens ONLY in
  `adapters.generator.MaterialResolver`, via `new NamespacedKey(namespace, value)` +
  `Registry.MATERIAL.get(key)` (this is the WorldEdit `BukkitAdapter` round-trip). The Gson
  `BlockId` adapter (string <-> `BlockId`) lives in `adapters.json`.
- This is the EngineHub/WorldEdit and FAWE pattern: `worldedit-core` identifies blocks by namespaced
  id (`BlockType implements Keyed`), and each platform resolves that id against its own registry. The
  same id works for Bukkit, Fabric, Forge, and Sponge.

Adding a platform (Fabric, ...) is then a new adapter that resolves `BlockId` to that platform's block
type; `api`, `ports`, and `core` do not change. See the `core-platform-neutrality` memory.

## Naming

The project was renamed from GridWFC to SkyGrid-ng. Project branded names use SkyGrid-ng
(`SkyGridNgPlugin`, `/skygrid`, permission `skygridng.admin`, i18n namespace `skygridng`). The WFC
names that remain (`WfcSolver`, `WfcSurfaceAlgorithm`, algorithm id `wfc`) describe the Wave Function
Collapse method, not the project, and must stay. Do not rename them.

## Adding things

The common extensions (a new algorithm, a new command subcommand, a new out adapter) each follow a
fixed recipe. Read `references/extending.md` and follow it step by step. The short version:

- New algorithm: add one class in `core.algorithm` implementing `ports.algorithm.TerrainAlgorithm`,
  add its id to `AlgorithmService` (factory plus `isKnown`) and to the command tab completion. The
  generator adapter never changes.
- New out adapter: implement the existing `ports.out` interface (in a new or existing adapter
  module), bind it in `RepositoryModule`. The core does not change.
- New OLF-only mechanic (`platforms/paper-olf`): implement `OlfFeature` (id/enable/disable), inject
  the `JavaPlugin`, the public `SkyGridApi`, or any port, and add one `Multibinder` line in
  `OlfModule`. `OlfNetworkPlugin` enables it with the lifecycle. It ships only in the unpublished OLF
  jar, so it never reaches Modrinth. Nothing in `api`, `ports`, `core`, the adapters, or
  `platforms/paper` changes.

## Verifying a core change compiles

The framework-free modules (api + ports + core) compile with plain `javac`, no Gradle or network
needed. Run `scripts/compile-core.sh` after an edit for a fast sanity check before the full build.

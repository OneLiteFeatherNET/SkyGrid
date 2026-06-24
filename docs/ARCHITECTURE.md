# Architecture: Hexagonal (Ports and Adapters)

The goal of the hexagonal architecture here: the domain logic (the generation engine) depends on
nothing, and everything external depends on it.

## The one rule

Dependencies always point inward:

```
   Platforms  ->  Adapters  ->  Ports  ->  API          (and Core -> Ports -> API)
 (paper, olf)   (generator,   (interfaces) (model +
                command, json)              facade)
```

The api, ports, and core never import Bukkit, Gson, or Adventure, and the adapters never import the
core. This is not left to discipline: the `verification` module runs ArchUnit tests on every build
that fail if any platform-neutral layer depends on a platform/framework package, if an adapter
depends on the core, or if the layered direction is broken.

## Module structure

The build is grouped under category folders; the module graph enforces the rule mechanically (an
adapter module has no `core` on its classpath):

| Module | Packages | May use frameworks | Artifact |
|--------|----------|--------------------|----------|
| `api` | `api.model`, `api.SkyGridApi`, `api.util` | no | published library |
| `ports` | `ports.in`, `ports.out`, `ports.algorithm` | no | library (depends on api) |
| `core` | `core.*` | no | library (depends on ports, api) |
| `adapters/generator` | `adapters.generator` | yes (Bukkit) | library (depends on ports, api) |
| `adapters/command` | `adapters.command` | yes (Bukkit, Adventure) | library (depends on ports, api) |
| `adapters/json` | `adapters.json` | yes (Gson) | library (depends on ports, api) |
| `platforms/common` | `platforms.common` | yes (Bukkit, Guice) | library (composition root) |
| `platforms/paper` | (none) | yes | `skygrid-ng-<version>.jar` |
| `platforms/paper-olf` | `platforms.olf` | yes | `skygrid-ng-olf-<version>.jar` |

`platforms/common` is the shared Bukkit composition root. `platforms/paper` (public) and
`platforms/paper-olf` (OneLiteFeather network) are sibling distributions that both build on it; the
OLF one layers OLF-only mechanics through the `OlfFeature` seam (see below). Adding another platform
(Fabric, Sponge) is a new sibling under `platforms/` that depends on the adapters and the core; api,
ports, core, and the existing adapters never change. The rings below describe the packages.

### Extensible composition root and OLF-only mechanics

`SkyGridNgPlugin` exposes seams so a distribution can add features without copying the wiring:
`modules()` (append Guice modules), `injector()` (read the graph), and
`onExtensionEnable()`/`onExtensionDisable()` (lifecycle hooks). `OlfNetworkPlugin` uses them to add
`OlfModule` and to enable every bound `OlfFeature`. An OLF-only mechanic (for example challenges) is
one `OlfFeature` (id/enable/disable) plus one `Multibinder` line; it may inject the `SkyGridApi`, any
core port, and the `JavaPlugin`. Because `platforms/paper-olf` is not published, these mechanics ship
to the OLF network only and never reach Modrinth or Hangar.

## The three rings

### 1. API (`api.*`)
Pure Java 25, the public data contract, fully testable without a server.

- `api.model` domain records (Flyweight: shared immutable `TileType`, `BlockWeight`): `TileType`,
  `FeatureSpec`, `AllowedPair`, `GenerationSettings`, `RuleSet`, `AdjacencyRules`, `Direction`, plus
  `BlockWeight`, `SkyGridConfig`, and the top level `GeneratorConfig`. Semantically versioned.
- `api.SkyGridApi` the public read-only facade, implemented by `core.service.SkyGridApiService` and
  registered as a Bukkit service by the composition root so other plugins and the OLF network can
  inspect a running generator. `api.util.Seeds` the SplitMix64 derivation, shared with the generator
  adapter.

### 2. Ports (`ports.*`)
The contracts. Interfaces only, framework free, depending on `api`. Adapters compile against these.

- `ports.in.GenerateRegionUseCase` what the world needs from the WFC core.
- `ports.in.RuleAdminUseCase` the in game administration.
- `ports.in.TerrainAlgorithmProvider` the active algorithm for the generator.
- `ports.out.RuleRepository` where configuration comes from and goes to (Repository).
- `ports.algorithm.TerrainAlgorithm` + `ColumnSink` the generation SPI the generator adapter drives.

In ports are implemented by the core and called by adapters. Out ports are called by the core and
implemented by adapters (dependency inversion).

### 3. Core (`core.*`)
Pure Java 25, the domain. Implements the ports; depends on `ports` + `api`.

- `core.solver` the WFC algorithm. `WfcSolver` plus two Strategy interfaces (`EntropyHeuristic`,
  `CollapseStrategy`). `core.util` `WeightedTable`, `ValueNoise`.
- `core.algorithm` the three `TerrainAlgorithm` implementations: `WfcSurfaceAlgorithm`,
  `IndependentSkyGridAlgorithm`, `NoiseSkyGridAlgorithm`.
- `core.state.RuleState` holds the current `GeneratorConfig`, Memento (undo) plus Observer.
- `core.service` the application services: `RegionGenerationService`, `AlgorithmService`,
  `RuleAdminService`, `SkyGridApiService`.

### 4. Adapters (`adapters.*`)
The translators to the outside world, one module each. Bukkit, Gson, and Adventure live here only;
they depend on `ports` + `api`, never on `core`.

- `adapters.generator` `HexagonalChunkGenerator` and `MaterialPopulator` call the use cases;
  `MaterialResolver` resolves a platform-neutral `BlockId` (`minecraft:stone`) to a Bukkit `Material`
  via `NamespacedKey` + `Registry.MATERIAL`. The core never names a Bukkit block (WorldEdit pattern).
- `adapters.command` `SkyGridCommand` calls `RuleAdminUseCase`; `Messages` builds translatable
  components.
- `adapters.json` `JsonRuleRepository` (plus `GeneratorConfigDto`) implements `RuleRepository` via
  Gson (Factory: JSON to domain), and owns the bundled default `generator.json`.

### Composition Root (`platforms.common`)
`SkyGridNgPlugin` builds a Guice object graph (`Stage.PRODUCTION`) from the modules in
`platforms.common.injection` (`PlatformModule`, `RepositoryModule`, `GeneratorModule`) and connects
adapters to ports. The core carries no injection annotations: its services are constructed in the
modules' `@Provides` methods, so the core stays framework free while the wiring is Guice managed.
Adapters (`HexagonalChunkGenerator`, `SkyGridCommand`) use constructor `@Inject`. `platforms.common`
`I18n` registers the Adventure translation store. The distributions (`platforms/paper`,
`platforms/paper-olf`) build on this shared root. Swapping an adapter changes a module binding, not the
core.

## Switchable algorithms

`TerrainAlgorithm.generateColumn(seed, x, z, minY, maxY, sink)` emits blocks for a column; the
Paper adapter writes them. `AlgorithmService` is the Factory (maps the `algorithm` config value to
an implementation) and an Observer on `RuleState` (rebuilds the active algorithm on change). The
generator only knows the `TerrainAlgorithmProvider` port.

Adding an algorithm: one class implementing `TerrainAlgorithm` plus one branch in
`AlgorithmService`. The generator adapter does not change.

## GoF patterns (source: refactoring.guru)

| Pattern | Where | Purpose |
|---------|-------|---------|
| Strategy | `EntropyHeuristic`, `CollapseStrategy`, `TerrainAlgorithm` | swap behavior without changing callers |
| Factory | `JsonRuleRepository`, `AlgorithmService` | build domain from JSON / build the active algorithm |
| Repository | `RuleRepository` | abstract the config source |
| Flyweight | `TileType`, `BlockWeight` | one shared instance per type |
| Observer | `RuleState` listeners | cache invalidation and algorithm rebuild |
| Memento | immutable `GeneratorConfig` + undo stack | in game undo |
| Command | `SkyGridCommand` subcommands | encapsulate runtime operations |

## Determinism and seamless WFC

Same world seed and coordinates always yield the same result. Seeds are derived reproducibly in
`Seeds`. WFC region grid lines are solved first as 1D WFC with anchor endpoints, then 2D region
interiors are solved against those fixed borders, so neighborships are always rule conformant
(verified: 0 adjacency violations over thousands of edges, 0 determinism deviations). The SkyGrid
algorithms are O(1) per grid cell and need no propagation.

## Internationalization

The core stays text free: services return data, the command adapter renders messages. `Messages`
builds `Component.translatable(key, args)`, `platforms.common` `I18n` registers the resource bundles as an
Adventure `TranslationStore` on the `GlobalTranslator`, and Paper renders each component in the
client locale.

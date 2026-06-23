# Architecture: Hexagonal (Ports and Adapters)

The goal of the hexagonal architecture here: the domain logic (the generation engine) depends on
nothing, and everything external depends on it.

## The one rule

Dependencies always point inward:

```
        Adapters   ->   Ports   ->   Core (domain + application)
   (Paper, JSON, Command)   (interfaces)        (generation)
```

The core never imports Bukkit, Gson, or Adventure. Verifiable at a glance: `core/` contains no
`import org.bukkit`, `import com.google.gson`, or `import net.kyori` lines.

## The three rings

### 1. Core (`core/`)
Pure Java 25, fully testable without a server.

- `core/model/` domain records (Flyweight: shared immutable `TileType`, `BlockWeight`):
  `TileType`, `FeatureSpec`, `AllowedPair`, `GenerationSettings`, `RuleSet`, `AdjacencyRules`,
  `Direction`, plus `BlockWeight`, `SkyGridConfig`, and the top level `GeneratorConfig`.
- `core/solver/` the WFC algorithm. `WfcSolver` plus two Strategy interfaces (`EntropyHeuristic`,
  `CollapseStrategy`), each with one implementation. Swappable without touching the solver.
- `core/algorithm/` the swappable generation algorithm port `TerrainAlgorithm` with `ColumnSink`,
  and three implementations: `WfcSurfaceAlgorithm`, `IndependentSkyGridAlgorithm`,
  `NoiseSkyGridAlgorithm`.
- `core/util/` `Seeds` (SplitMix64), `WeightedTable`, `ValueNoise`.
- `core/state/` `RuleState`: holds the current `GeneratorConfig`, manages Memento (undo stack of
  immutable snapshots) and Observer (change listeners).
- `core/service/` the application services: `RegionGenerationService` (solves and caches WFC
  regions), `AlgorithmService` (Factory plus Observer for the active algorithm), `RuleAdminService`
  (validates in game edits).

### 2. Ports (`core/port/`)
The contracts. Interfaces only, part of the core.

- `port/in/GenerateRegionUseCase` what the world needs from the WFC core.
- `port/in/RuleAdminUseCase` the in game administration.
- `port/in/TerrainAlgorithmProvider` the active algorithm for the generator.
- `port/out/RuleRepository` where configuration comes from and goes to (Repository).

In ports are implemented by the core and called by adapters. Out ports are called by the core and
implemented by adapters (dependency inversion).

### 3. Adapters (`adapter/`)
The translators to the outside world. Bukkit, Gson, and Adventure live here only.

- `adapter/in/paper/` `HexagonalChunkGenerator` and `MaterialPopulator` call the use cases;
  `MaterialResolver` maps names to `Material`.
- `adapter/in/command/` `WfcCommand` calls `RuleAdminUseCase`; `Messages` builds translatable
  components.
- `adapter/out/json/` `JsonRuleRepository` (plus `GeneratorConfigDto`) implements `RuleRepository`
  via Gson (Factory: JSON to domain).

### Composition Root (`bootstrap/`)
`GridWfcPlugin` builds a Guice object graph (`Stage.PRODUCTION`) from the modules in
`bootstrap/injection/` (`PlatformModule`, `RepositoryModule`, `GeneratorModule`) and connects
adapters to ports. The core carries no injection annotations: its services are constructed in the
modules' `@Provides` methods, so the core stays framework free while the wiring is Guice managed.
Adapters (`HexagonalChunkGenerator`, `WfcCommand`) use constructor `@Inject`. `bootstrap/I18n`
registers the Adventure translation store. Swapping an adapter changes a module binding, not the
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
| Command | `WfcCommand` subcommands | encapsulate runtime operations |

## Determinism and seamless WFC

Same world seed and coordinates always yield the same result. Seeds are derived reproducibly in
`Seeds`. WFC region grid lines are solved first as 1D WFC with anchor endpoints, then 2D region
interiors are solved against those fixed borders, so neighborships are always rule conformant
(verified: 0 adjacency violations over thousands of edges, 0 determinism deviations). The SkyGrid
algorithms are O(1) per grid cell and need no propagation.

## Internationalization

The core stays text free: services return data, the command adapter renders messages. `Messages`
builds `Component.translatable(key, args)`, `bootstrap/I18n` registers the resource bundles as an
Adventure `TranslationStore` on the `GlobalTranslator`, and Paper renders each component in the
client locale.

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

The build has three Gradle modules. The `domain` module holds the framework-free packages; the
`plugin` module holds everything that talks to Paper and is the single distribution; `verification`
holds the ArchUnit rules. Packages keep their names, so the hexagonal boundaries are still enforced
package by package (ArchUnit), and the module split only decides what may use which framework.

| Module | Packages | May use frameworks | Artifact |
|--------|----------|--------------------|----------|
| `domain` | `api.model`, `api.SkyGridApi`, `api.util` | no | published library |
| `domain` | `ports.in`, `ports.out`, `ports.algorithm` | no | part of the domain module |
| `domain` | `core.*` | no | part of the domain module |
| `plugin` | `adapters.generator`, `adapters.spawn` | no (Adventure only) | part of the plugin module |
| `plugin` | `adapters.command`, `adapters.command.handler` | yes (Bukkit, Adventure, Incendo Cloud) | part of the plugin module |
| `plugin` | `adapters.config` | yes (Jackson) | part of the plugin module |
| `plugin` | `platforms.common`, `platforms.olf` | yes (Bukkit, Guice) | composition root |
| `plugin` | (none) | yes | `skygrid-ng-<version>.jar` |
| `verification` | ArchUnit tests | n/a | none |

`platforms.common` is the shared Bukkit composition root; the plugin module's build file adds the
plugin.yml, shadow, publishing and run-paper wiring around it. `platforms.olf` inside
`plugin` is a naming convention, not a separate module: it groups mechanics specific to
this OneLiteFeather (OLF) server (challenges) through the `OlfFeature` seam (see below), always
wired and enabled, since there is only one distribution. The rings below describe the packages.

### Extensible composition root and OLF-only mechanics

`SkyGridNgPlugin` exposes seams a subclass or future distribution could use without copying the
wiring: `modules()` (append Guice modules), `injector()` (read the graph), and
`onExtensionEnable()`/`onExtensionDisable()` (lifecycle hooks); today `SkyGridNgPlugin` itself uses
them directly to add `OlfModule` and enable every bound `OlfFeature`. An OLF-only mechanic (for
example challenges) is one `OlfFeature` (id/enable/disable) plus one `Multibinder` line in
`platforms.olf`; it may inject the `SkyGridApi`, any core port, and the `JavaPlugin`. The `platforms.olf`
naming marks these as OneLiteFeather-server-specific by convention, not as a separately published
artifact.

## The three rings

### 1. API (`api.*`)
Pure Java 25, the public data contract, fully testable without a server.

- `api.model` domain records (Flyweight: shared immutable `BlockWeight`, `Preset`/`Rule` entries):
  `BlockId`, `BiomeId`, `BlockWeight`, `SkyGridConfig`, `SpawnConfig`, `SpawnPoint`, and the top
  level `GeneratorConfig`, plus the preset/rule records (`Preset`, `PresetCatalog`, `Rule`,
  `RuleCatalog`, `PlacementRule`, `StructurePattern`, `LegendEntry`, `Plant`, `PhysicsMode`) and the
  challenge records (`ChallengeRule`, `ChallengeCatalog`). Semantically versioned. The former Wave
  Function Collapse model (`TileType`, `FeatureSpec`, `AllowedPair`, `GenerationSettings`, `RuleSet`,
  `AdjacencyRules`, `Direction`) was removed with the `wfc` algorithm.
- `api.SkyGridApi` the public read-only facade, implemented by `core.service.SkyGridApiService` and
  registered as a Bukkit service by the composition root so other plugins and the OLF network can
  inspect a running generator. `api.util.Seeds` the SplitMix64 derivation, shared with the generator
  adapter.

### 2. Ports (`ports.*`)
The contracts. Interfaces only, framework free, depending on `api`. Adapters compile against these.

- `ports.in.RuleAdminUseCase` the in game administration; `ports.in.Presets` preset activation and
  world assignment; `ports.in.ChallengeAdminUseCase` challenge rule administration.
- `ports.in.TerrainAlgorithms` the active-algorithm catalog for the generator (named after the
  catalog it exposes, not `TerrainAlgorithmProvider`); `ports.in.Biomes`, `ports.in.SkyGridDecorations`,
  `ports.in.SpawnPoints` the biome, decoration, and spread-spawn catalogs.
- `ports.out.RuleRepository`, `ports.out.PresetRepository`, `ports.out.ChallengeRepository` where
  configuration comes from and goes to (Repository); see `config-schema-and-rules` and
  `challenge-season-system`.
- `ports.algorithm.TerrainAlgorithm` + `ColumnSink` the generation SPI the generator adapter drives.

In ports are implemented by the core and called by adapters. Out ports are called by the core and
implemented by adapters (dependency inversion).

### 3. Core (`core.*`)
Pure Java 25, the domain. Implements the ports; depends on `ports` + `api`.

- `core.util` `WeightedTable`, `ValueNoise`.
- `core.algorithm` the two `TerrainAlgorithm` implementations: `IndependentSkyGridAlgorithm`,
  `NoiseSkyGridAlgorithm`.
- `core.state.RuleState` holds the current `GeneratorConfig`, Memento (undo) plus Observer.
- `core.service` the application services: `RuleAdminService`, `PresetService`, `PresetResolver`,
  `SkyGridApiService`, `ChallengeAdminService`, `DefaultTerrainAlgorithms`, `DefaultBiomes`,
  `DefaultSkyGridDecorations`, `SpiralSpawnService`.

### 4. Adapters (`adapters.*`)
The translators to the outside world, one module each. Bukkit, Jackson, and Adventure live here
only; they depend on `ports` + `api`, never on `core`.

- `adapters.generator` `HexagonalChunkGenerator` calls the use cases; `MaterialResolver` resolves a
  platform-neutral `BlockId` (`minecraft:stone`) to a Bukkit `Material` via `NamespacedKey` +
  `Registry.MATERIAL`. The core never names a Bukkit block (WorldEdit pattern).
- `adapters.command` runs `/skygrid` on the Incendo Cloud command framework: `CloudCommandManagerFactory`
  builds a `PaperCommandManager<CommandSource>` (Cloud's Paper sender mapped to our own
  platform-neutral `CommandSource` via `SenderMapper`), `CloudCommandRegistrar` wires suggestion
  providers and permission handling and parses the `@Command`-annotated handler classes in
  `adapters.command.handler` (`PresetCommands`, `AlgorithmCommands`, `MaintenanceCommands`,
  `ChallengeCommands`, `HelpCommands`), each calling `RuleAdminUseCase`/`Presets`/
  `ChallengeAdminUseCase`; `Messages` builds translatable components (MiniMessage, not
  `java.text.MessageFormat`).
- `adapters.config` `JacksonPresetRepository`/`JacksonRuleRepository`/`JacksonChallengeRepository`
  (Factory: JSON/YAML/TOML to domain, format chosen per file by extension) implement
  `PresetRepository`/`RuleRepository`/`ChallengeRepository` via Jackson, mapped through
  `ConfigDtos`/`RuleDtos`/`ChallengeDtos`, and own the bundled default `presets/`, `rules/`, and
  `challenges/` resources. `adapters.spawn` `SpreadSpawnListener`/`SafeLocationFinder` place new
  players. `platforms.olf.challenge` the OneLiteFeather challenge rule listeners.

### Composition Root (`platforms.common`)
`SkyGridNgPlugin` builds a Guice object graph (`Stage.PRODUCTION`) from the modules in
`platforms.common.injection` (`PlatformModule`, `RepositoryModule`, `GeneratorModule`,
`CommandModule`) and connects adapters to ports. The core carries no injection annotations: its
services are constructed in the modules' `@Provides` methods, so the core stays framework free while
the wiring is Guice managed. Adapters (`HexagonalChunkGenerator`, the `adapters.command.handler`
classes) use constructor `@Inject`; the Cloud handler set is a Guice `Multibinder` (`CommandModule`),
parsed as a whole by `CloudCommandRegistrar` (see `hexagonal-architecture`'s "Add a command
subcommand" recipe). `platforms.common` `I18n` registers the MiniMessage translation store.
`plugin` builds on this shared root. Swapping an adapter changes a module binding, not the
core.

## Switchable algorithms

`TerrainAlgorithm.generateColumn(seed, x, z, minY, maxY, sink)` emits blocks for a column; the
Paper adapter writes them. `DefaultTerrainAlgorithms` is the Factory (a `Registry<String,
AlgorithmFactory>` maps the `algorithm` config value to an implementation) and an Observer on
`RuleState` (rebuilds the active algorithm on change). The generator only knows the
`ports.in.TerrainAlgorithms` port.

Adding an algorithm: one class implementing `TerrainAlgorithm` plus one `AlgorithmFactory`
registration in the composition root's `MapBinder` (read by `DefaultTerrainAlgorithms`'s registry,
the single source of truth for known ids, not a `switch`). The generator adapter does not change.

## GoF patterns (source: refactoring.guru)

| Pattern | Where | Purpose |
|---------|-------|---------|
| Strategy | `TerrainAlgorithm` | swap behavior without changing callers |
| Factory | `Jackson*Repository`, `DefaultTerrainAlgorithms`'s `AlgorithmFactory` registry | build domain from config / build the active algorithm |
| Repository | `RuleRepository`, `PresetRepository`, `ChallengeRepository` | abstract the config source |
| Flyweight | `BlockWeight`, `Preset`/`Rule` entries | one shared instance per type |
| Observer | `RuleState` listeners | cache invalidation and algorithm rebuild |
| Memento | immutable `GeneratorConfig` + undo stack | in game undo |
| Command | Incendo Cloud `@Command` handlers (`adapters.command.handler`) | encapsulate runtime operations |

## Determinism

Same world seed and coordinates always yield the same result. Seeds are derived reproducibly in
`Seeds`. The SkyGrid algorithms are O(1) per grid cell and need no propagation, so generation stays
fast and parallel safe at any player count.

## Internationalization

The core stays text free: services return data, the command adapter renders messages. `Messages`
builds `Component.translatable(key, args)`, `platforms.common` `I18n` registers the resource bundles
as a `MiniMessageTranslationStore` on the `GlobalTranslator` (bundle values are MiniMessage, not
`java.text.MessageFormat`; arguments are `<arg:0>`/`<arg:1>`), and Paper renders each component in
the client locale.

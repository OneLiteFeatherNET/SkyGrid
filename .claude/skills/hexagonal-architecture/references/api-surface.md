# API surface

Exact signatures and contracts. Verified against the source. Read the relevant section before
editing or calling a type so you match the real API instead of guessing.

## Contents

- Model records
- Util
- Algorithm
- State
- Ports
- Services
- Adapters
- Bootstrap and Guice modules

## Model records (`api/model`)

All compact constructors validate and throw `IllegalArgumentException` on bad input. The former
Wave Function Collapse model (`TileType`, `FeatureSpec`, `RuleSet`, `AllowedPair`, `AdjacencyRules`,
`GenerationSettings`, `Direction`) was removed along with the `wfc` algorithm; do not reintroduce it.

- `GeneratorConfig(String algorithm, SkyGridConfig skygrid, SpawnConfig spawn, List<BiomeId> biomes)`
  with `withAlgorithm`, `withSkygrid`, `withSpawn`, `withBiomes`. `algorithm` is `"skygrid"` or
  `"noise_skygrid"`; blank algorithm, a null section, or an empty biome list throws. Two convenience
  constructors fill in default spawn and/or biomes for older call sites.
- `SkyGridConfig(int spacing, int minY, int maxY, double noiseScale, List<BlockWeight> blocks)` with
  `withBlockWeight(String,double):SkyGridConfig`. `spacing < 1`, `maxY < minY`, `noiseScale <= 0`,
  or empty blocks throws.
- `SpawnConfig`: the spread first-join spawn settings (`spread`, `spacing`, `searchRadius`,
  `maxAttempts`); static `defaults()`. Replaces the removed starter-platform fields
  (`platformY`/`platformBlock`/`platformRadius`/`villager`).
- `BlockWeight(String material, double weight)`. Blank material or weight <= 0 throws.
- `BlockId`, `BiomeId`: namespaced ids (`minecraft:stone`), validated and lowercased.
- The preset and per-block rule records (`Preset`, `PresetCatalog`, `Rule`, `RuleCatalog`,
  `PlacementRule`, `StructurePattern`, `Plant`, `PhysicsMode`, `SpawnPoint`, `LegendEntry`) are the
  domain of the `config-schema-and-rules` skill; read that skill for their exact fields instead of
  guessing here.

## Util (`core/util`)

- `Seeds` (static, SplitMix64): `mix(long,int,int)`, `mix3(long,int,int,int)`,
  `regionSeed/cellSeed/boundarySeed(long,int,int)`, `blockSeed(long,int,int,int)`,
  `rng(long):RandomGenerator` (L64X128MixRandom), `unitDouble(long):double` in `[0,1)`. Deterministic.
- `WeightedTable`: static `of(List<BlockWeight>):WeightedTable`; `sample(double roll):String`
  (roll clamped to `[0, 0.999999999]`, binary search on cumulative weights); `totalWeight():double`;
  `size():int`.
- `ValueNoise`: `new ValueNoise(long seed)`; `sample(double x,double y,double z):double` in `[0,1)`,
  trilinear with smoothstep, stable per integer lattice point.

## Algorithm (`core/algorithm`)

The `core/solver` package (`WfcSolver`, `EntropyHeuristic`, `CollapseStrategy`,
`ShannonEntropyHeuristic`, `WeightedRandomCollapse`) was removed along with the `wfc` algorithm.

- `TerrainAlgorithm`: `String id()`; `void generateColumn(long worldSeed, int worldX, int worldZ,
  int worldMinY, int worldMaxY, ColumnSink sink)`.
- `ColumnSink` (`@FunctionalInterface`): `void place(int y, String material)`. The only seam the
  Paper adapter implements; the core emits `(y, materialName)` and never sees Bukkit.
- `IndependentSkyGridAlgorithm(SkyGridConfig)`, id `skygrid`. One independent weighted roll per grid
  cell; off grid columns are empty (void).
- `NoiseSkyGridAlgorithm(SkyGridConfig)`, id `noise_skygrid`. Same grid, block chosen from a value
  noise field so neighbours cluster.

## State (`core/state`)

- `RuleState(GeneratorConfig initial, int maxUndo)`. `current():GeneratorConfig` (lock free).
  `apply(GeneratorConfig)` pushes the old config as a Memento (bounded by `maxUndo`) and fires
  listeners. `replace(GeneratorConfig)` sets and clears the undo stack and fires. `undo():boolean`
  pops or returns false. `addChangeListener(Runnable)`.

## Ports

The `GenerateRegionUseCase` port (the WFC region solver's in port) was removed with the `wfc`
algorithm. There is no replacement; the two remaining algorithms need no region solving step.

- `generator.RuleAdminUseCase`: `current():GeneratorConfig`, `algorithm():String`,
  `setAlgorithm(String):boolean`, `reload()`, `save()`, `setSkyGridWeight(String,double):boolean`,
  `undo():boolean`. The WFC tile admin methods (`tileIds`, `tile`, `setWeight`, `allow`, `disallow`)
  are gone; there is no tile concept left to administer.
- `generator.TerrainAlgorithms`: `current():TerrainAlgorithm`, `isKnown(String):boolean`,
  `ids():Set<String>`. Named after the catalog it exposes, not `TerrainAlgorithmProvider`.
- `generator.AlgorithmFactory` (`@FunctionalInterface`): `TerrainAlgorithm create(GeneratorConfig)`.
  A Factory Method, one registered per algorithm id via a Guice `MapBinder` in the composition root.
- `generator.RuleRepository`: `RuleCatalog load()` (creates the bundled defaults if `rules/` is
  empty); `void save(RuleCatalog)`.
- `generator.PresetRepository`: `PresetCatalog load()` (creates defaults if missing);
  `void save(PresetCatalog)`.
- `challenge.ChallengeRepository`, `generator.Presets`, `generator.Biomes`, `generator.SkyGridDecorations`,
  `generator.SpawnPoints`, `challenge.ChallengeAdminUseCase`: the preset/rules, biome, spawn, and
  challenge ports; the domain of the `config-schema-and-rules` (presets/rules) and
  `challenge-season-system` (challenges) skills, not detailed here.

## Services (`core/service`)

The former `RegionGenerationService` (implemented `GenerateRegionUseCase` with a `WfcSolver`) and
`AlgorithmService` were removed or renamed when the `wfc` algorithm was removed.

- `DefaultTerrainAlgorithms(RuleState, Map<String, AlgorithmFactory>)` implements `TerrainAlgorithms`.
  Holds a `Registry<String, AlgorithmFactory>` built from the injected factory map (so the registry's
  key set, not a `switch`, is the single source of truth for known ids); rebuilds the active algorithm
  on every `RuleState` change (Observer), falling back to `IndependentSkyGridAlgorithm` for an unknown
  configured id.
- `RuleAdminService(RuleState, PresetService, TerrainAlgorithms)` implements `RuleAdminUseCase`;
  `save()`/`reload()` delegate to `PresetService`, which flattens edits back into the active preset
  (or reloads the catalog from disk) via `PresetRepository`. `PresetService` and `PresetResolver`
  (preset `extends` inheritance) are the domain of `world-generator-presets`, not detailed here.

## Adapters

- `generator.HexagonalChunkGenerator(TerrainAlgorithms, SkyGridDecorations, Biomes,
  RuleCatalog)` extends `ChunkGenerator`, `@Inject`. Builds a `RuleResolver(ruleCatalog,
  Material.STONE)` field and a `BiomeResolver(Biome.PLAINS)`. `generateSurface` reads
  `worldInfo.getSeed()` and `chunkData.getMinHeight()/getMaxHeight()`, then delegates per column to
  the active algorithm. All `shouldGenerate*` return false (fully custom), `isParallelCapable()`
  true. `getDefaultBiomeProvider` returns a `SkyGridBiomeProvider(biomes, biomeResolver)`.
- `command.CloudCommandManagerFactory.create(JavaPlugin)` builds an Incendo Cloud
  `PaperCommandManager<CommandSource>` via `SenderMapper.create(PaperCommandSource::new,
  PaperCommandSource::stack)`, so the command tree never touches a raw Paper `CommandSourceStack`.
  `command.CloudCommandRegistrar.register(manager, presets, admin, challenges,
  Set<CloudCommandHandler>)` registers the dynamic suggestion providers (`preset-names`,
  `algorithm-ids`, `material-ids`, `challenge-ids`), a `NoPermissionException` handler that renders
  `Messages.noPermission()`, and parses every handler in the set via `AnnotationParser`.
- `command` (Bukkit/Paper/cloud-paper/cloud-brigadier free, ArchUnit enforced):
  `CommandSource` (`sendMessage(Component)`, `hasPermission(String)`), `CloudCommandHandler` (marker,
  bound in `command.CommandModule`'s `Multibinder`), and the five
  `@Command`/`@Argument`/`@Permission`-annotated handler classes: `PresetCommands` (`preset`,
  `presets`, `worlds`), `AlgorithmCommands` (`algorithm`, `skyweight`), `MaintenanceCommands`
  (`reload`, `reload confirm`, `save`, `undo`), `ChallengeCommands` (`challenge list`,
  `challenge toggle`), `HelpCommands` (bare `skygrid`, `skygrid help`, `skygrid help <subcommand>`).
- `config.JacksonPresetRepository(Path dataFolder, BundledDefaults)` implements
  `PresetRepository`; `config.JacksonRuleRepository(Path dataFolder, BundledDefaults)`
  implements `RuleRepository`. Both read/write the split `config.<ext>`/`presets/`/`rules/` layout
  (JSON, YAML, or TOML per file, chosen by extension) via Jackson; see `config-schema-and-rules` for
  the full format. `config.JacksonChallengeRepository` implements `ChallengeRepository` the
  same way for `challenges/`.

## Bootstrap and Guice modules (`bootstrap`)

- `SkyGridNgPlugin extends JavaPlugin`. `onLoad` builds the injector
  (`Guice.createInjector(Stage.PRODUCTION, new PlatformModule(this), new RepositoryModule(),
  new GeneratorModule())`). `onEnable` registers the i18n store, builds the Cloud command manager and
  registers its handlers (guarded: a construction/registration failure is logged, not fatal, see
  `CloudCommandManagerFactory`/`CloudCommandRegistrar`), and logs. `getDefaultWorldGenerator` returns
  `injector.getInstance(HexagonalChunkGenerator.class)`.
- `injection/PlatformModule(JavaPlugin)` binds `JavaPlugin` and provides the data folder `Path`.
- `injection/RepositoryModule` provides `PresetRepository` and `RuleRepository`, both backed by
  `JacksonPresetRepository`/`JacksonRuleRepository` seeded from `BundledDefaults` (the plugin jar's
  bundled resources via `JavaPlugin.getResource`).
- `injection/GeneratorModule` provides the core services and binds each in port to its service. The
  core stays annotation free; this module is the only place the core is constructed.
- `injection/CommandModule` binds every `command.CloudCommandHandler` into a Guice
  set binder, parsed as a whole by `CloudCommandRegistrar` (see the Adapters section above).

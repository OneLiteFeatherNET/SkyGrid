# API surface

Exact signatures and contracts. Verified against the source. Read the relevant section before
editing or calling a type so you match the real API instead of guessing.

## Contents

- Model records
- Util
- Solver
- Algorithm
- State
- Ports
- Services
- Adapters
- Bootstrap and Guice modules

## Model records (`core/model`)

All compact constructors validate and throw `IllegalArgumentException` on bad input.

- `TileType(String id, double weight, boolean neutral, String surface, String fill, boolean liquid,
  List<FeatureSpec> features)`. `withWeight(double)` returns a copy. Blank id or weight <= 0 throws;
  null features becomes an empty list.
- `FeatureSpec(String material, boolean requiresLiquidNeighbor, String requiresGroundBlock,
  double probability, int minHeight, int maxHeight)`. Blank material, `minHeight < 1`,
  `maxHeight < minHeight`, or probability outside `[0,1]` throws.
- `GenerationSettings(int cellSize, int regionSize, int surfaceY, int maxSolverAttempts,
  String fallbackTileId, boolean allowSelf)`. `cellSize < 1`, `regionSize < 2`,
  `maxSolverAttempts < 1`, or blank fallback throws.
- `GeneratorConfig(String algorithm, RuleSet wfc, SkyGridConfig skygrid)` with
  `withAlgorithm`, `withWfc`, `withSkygrid`. Blank algorithm or null sections throw.
- `RuleSet(List<TileType> tiles, Set<AllowedPair> allowed, GenerationSettings settings)`. Empty
  tiles, more than 64 tiles, or a fallback id not in tiles throws. Methods:
  `buildAdjacency():AdjacencyRules`, `tileById(String):Optional<TileType>`,
  `tileIndex(String):int` (throws on unknown id), `tileIds():List<String>`, `fullMask():long`,
  `neutralMask():long`, `withTileWeight(String,double):RuleSet`, `withAllowed(String,String):RuleSet`,
  `withoutAllowed(String,String):RuleSet`.
- `SkyGridConfig(int spacing, int minY, int maxY, double noiseScale, List<BlockWeight> blocks)` with
  `withBlockWeight(String,double):SkyGridConfig`. `spacing < 1`, `maxY < minY`, `noiseScale <= 0`,
  or empty blocks throws.
- `BlockWeight(String material, double weight)`. Blank material or weight <= 0 throws.
- `AllowedPair(String a, String b)`. Null or blank endpoint throws.
- `Direction` enum with `dx()`, `dz()`.
- `AdjacencyRules`: `MAX_TILES = 64`. Static `from(List<TileType> tiles, Set<AllowedPair> allowed,
  boolean allowSelf):AdjacencyRules` (more than 64 tiles throws). `support(long domainMask,
  Direction):long` returns the union of allowed neighbour bits. `tileCount():int`.

## Util (`core/util`)

- `Seeds` (static, SplitMix64): `mix(long,int,int)`, `mix3(long,int,int,int)`,
  `regionSeed/cellSeed/boundarySeed(long,int,int)`, `blockSeed(long,int,int,int)`,
  `rng(long):RandomGenerator` (L64X128MixRandom), `unitDouble(long):double` in `[0,1)`. Deterministic.
- `WeightedTable`: static `of(List<BlockWeight>):WeightedTable`; `sample(double roll):String`
  (roll clamped to `[0, 0.999999999]`, binary search on cumulative weights); `totalWeight():double`;
  `size():int`.
- `ValueNoise`: `new ValueNoise(long seed)`; `sample(double x,double y,double z):double` in `[0,1)`,
  trilinear with smoothstep, stable per integer lattice point.

## Solver (`core/solver`)

- `EntropyHeuristic` (`@FunctionalInterface`): `double entropy(long domainMask, List<TileType> tiles)`.
  Smaller collapses first.
- `CollapseStrategy` (`@FunctionalInterface`): `int collapse(long domainMask, List<TileType> tiles,
  RandomGenerator random)`. Returns an index whose bit is set in the mask.
- `ShannonEntropyHeuristic`, `WeightedRandomCollapse`: the default strategies.
- `WfcSolver(EntropyHeuristic, CollapseStrategy)`. Inner `@FunctionalInterface CellPreset` with
  `long maskAt(int localX, int localZ)`. Method:
  `int[] solve(int width, int height, long baseSeed, AdjacencyRules adjacency, List<TileType> tiles,
  long fullMask, int maxAttempts, int fallbackIndex, CellPreset preset)`. Retries with a salted seed
  on contradiction; after `maxAttempts` fills every cell with `fallbackIndex`.

## Algorithm (`core/algorithm`)

- `TerrainAlgorithm`: `String id()`; `void generateColumn(long worldSeed, int worldX, int worldZ,
  int worldMinY, int worldMaxY, ColumnSink sink)`; `default boolean usesWfcDecorations()` (false).
- `ColumnSink` (`@FunctionalInterface`): `void place(int y, String material)`. The only seam the
  Paper adapter implements; the core emits `(y, materialName)` and never sees Bukkit.
- `IndependentSkyGridAlgorithm(SkyGridConfig)`, id `skygrid`. One independent weighted roll per grid
  cell; off grid columns are empty (void).
- `NoiseSkyGridAlgorithm(SkyGridConfig)`, id `noise_skygrid`. Same grid, block chosen from a value
  noise field so neighbours cluster.
- `WfcSurfaceAlgorithm(GenerateRegionUseCase)`, id `wfc`, `usesWfcDecorations()` true. Solid column
  up to `surfaceY`, top block is the surface material of the WFC tile at that cell.

## State (`core/state`)

- `RuleState(GeneratorConfig initial, int maxUndo)`. `current():GeneratorConfig` (lock free).
  `apply(GeneratorConfig)` pushes the old config as a Memento (bounded by `maxUndo`) and fires
  listeners. `replace(GeneratorConfig)` sets and clears the undo stack and fires. `undo():boolean`
  pops or returns false. `addChangeListener(Runnable)`.

## Ports

- `core/port/in/GenerateRegionUseCase`: `TileType tileAt(long worldSeed, int cellX, int cellZ)`;
  `RuleSet rules()`.
- `core/port/in/RuleAdminUseCase`: `current()`, `algorithm()`, `setAlgorithm(String):boolean`,
  `tileIds():List<String>`, `tile(String):Optional<TileType>`, `reload()`, `save()`,
  `setWeight(String,double):boolean`, `allow(String,String):boolean`,
  `disallow(String,String):boolean`, `setSkyGridWeight(String,double):boolean`, `undo():boolean`.
- `core/port/in/TerrainAlgorithmProvider`: `TerrainAlgorithm current()`.
- `core/port/out/RuleRepository`: `GeneratorConfig load()` (creates defaults if missing);
  `void save(GeneratorConfig)`.

## Services (`core/service`)

- `RegionGenerationService(RuleState, WfcSolver)` implements `GenerateRegionUseCase`.
- `AlgorithmService(RuleState, GenerateRegionUseCase)` implements `TerrainAlgorithmProvider`. Static
  `isKnown(String):boolean`. Rebuilds the active algorithm when the state changes (Observer).
- `RuleAdminService(RuleState, RuleRepository)` implements `RuleAdminUseCase`.

## Adapters

- `adapter/in/paper/HexagonalChunkGenerator(TerrainAlgorithmProvider, GenerateRegionUseCase)` extends
  `ChunkGenerator`, `@Inject`. Field `materials = new MaterialResolver(Material.STONE)`.
  `generateSurface` reads `worldInfo.getSeed()` and `chunkData.getMinHeight()/getMaxHeight()`, then
  delegates per column to the active algorithm. All `shouldGenerate*` return false (fully custom),
  `isParallelCapable()` true. `getDefaultPopulators` returns a `MaterialPopulator` only when
  `usesWfcDecorations()`.
- `adapter/in/paper/MaterialResolver` (package private): `MaterialResolver(Material fallback)`;
  `Material resolve(String name)` (cached; unknown name maps to the fallback).
- `adapter/in/command/SkyGridCommand(RuleAdminUseCase)` implements `CommandExecutor, TabCompleter`,
  `@Inject`. `onCommand` checks `sender.hasPermission("skygridng.admin")`, then dispatches the
  subcommands `list, info, weight, allow, disallow, algorithm, skyweight, reload, save, undo, help`.
- `adapter/out/json/JsonRuleRepository(Path file, String defaultJson)` implements `RuleRepository`.

## Bootstrap and Guice modules (`bootstrap`)

- `SkyGridNgPlugin extends JavaPlugin`. `onLoad` builds the injector
  (`Guice.createInjector(Stage.PRODUCTION, new PlatformModule(this), new RepositoryModule(),
  new GeneratorModule())`). `onEnable` registers the i18n store, sets the `skygrid` command executor,
  and logs. `getDefaultWorldGenerator` returns `injector.getInstance(HexagonalChunkGenerator.class)`.
- `injection/PlatformModule(JavaPlugin)` binds `JavaPlugin` and provides the data folder `Path`.
- `injection/RepositoryModule` provides `RuleRepository` from `plugin.getResource("generator.json")`.
- `injection/GeneratorModule` provides the core services and binds each in port to its service. The
  core stays annotation free; this module is the only place the core is constructed.

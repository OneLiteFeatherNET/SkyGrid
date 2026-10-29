---
name: hexagonal-architecture
description: >
  Use whenever editing, extending, or reviewing SkyGrid-ng Java code: the core domain, ports,
  adapters, services, the Paper chunk generator, the /skygrid command, the JSON repository, or
  the Guice composition root. Enforces the hexagonal dependency rule (adapter -> port -> core),
  keeps the core free of Bukkit, Gson, Guice, and jakarta.inject (Adventure text is allowed), and provides the
  recipes for adding a generation algorithm, a command subcommand, or an out adapter. Read this
  before touching any Java file so a change does not leak framework code into the core or wire a
  dependency the wrong way.
---

# Hexagonal architecture for SkyGrid-ng

The whole point of this layout is that the generation logic is testable without a server and that
swapping an adapter (JSON for a database, Bukkit for another platform) never touches the core. Keep
that property intact.

## The one rule

The code is packaged by feature. Each feature package name exists in both modules; the domain logic
lives in `domain`, the Bukkit, Guice, Jackson and JPA code in `plugin`. The root package
`net.onelitefeather.skygridng` holds the composition root only. See `CLAUDE.md` for the feature map.

- `domain` is pure Java. It must not import `org.bukkit.*`, `com.fasterxml.*`, `com.google.inject.*`,
  `jakarta.inject.*`, `jakarta.persistence.*`, `org.hibernate.*` or `net.kyori.adventure.platform.*`
  (Adventure text is allowed). If you are about to add one of those, stop: the logic belongs in the
  plugin module, or the domain is missing a value type.
- Season must not depend on challenge. Challenge may depend on season.
- The composition root (root package and the plugin feature modules) is the only place that knows every
  concrete class.

## Enforcement (ArchUnit)

The `:verification` module runs ArchUnit tests (`ArchitectureTest`) on every build. They assert that the
domain classes (located by their module jar) depend on no platform or persistence framework package, that
the domain never reads the system clock, that `@Entity` classes live in the plugin module, that no code
uses `DriverManager` or a native query without a result type, and that feature packages form no cycles.
Adventure text (`net.kyori.adventure`) is allowed everywhere; only its platform bindings are banned. The
module graph already blocks most violations at compile time, but ArchUnit is the net that survives
build-file drift.

## Module layout

The build is multi-module, grouped under category folders. The dependency rule is enforced at the
build level by the module graph (an adapter module simply has no `core` on its classpath) and by
ArchUnit in `:verification`:

- `api` (`api.*`): the public, framework-free contract. The immutable model (`api.model`), the
  `SkyGridApi` facade, and `generator.Seeds`. No dependencies beyond the JDK. Published.
- `ports` (`ports.*`): the interfaces adapters compile against, framework free. `ports.in`,
  `ports.out`, `generator`. Depends on `api`.
- `core` (`core.*`): the framework-free domain (algorithms, solver, services, state). Implements the
  ports; depends on `ports` + `api` with the `api` configuration.
- `plugin` (`generator`): framework-free, like `core`, not Bukkit/Gson glue.
  Depends on `ports` + `api` only and resolves nothing through a native type directly; it drives
  decoration, structure, and still-water placement entirely through `generator.RegionWriter`,
  the seam whose Bukkit implementation (`LimitedRegionWriter`, wrapping a live `LimitedRegion`) lives
  in `plugin` alongside the rest of the Bukkit-flavored generator classes
  (`HexagonalChunkGenerator`, `RuleResolver`, `MaterialResolver`, `BiomeResolver`).
- `generator` (in `plugin`): framework-free, like `core`, not Bukkit glue. Depends on
  `ports` + `api` only and resolves nothing through a native type directly; it drives the first-join
  safe-spawn search entirely through `generator.GroundReader`, the seam whose Bukkit
  implementation (`BukkitGroundReader`, wrapping a live `World`) lives in `plugin`
  alongside the rest of the Bukkit-flavored spawn classes (`SpreadSpawnListener`).
- `plugin` (`command`): framework-free, like `core`, not Bukkit glue. Depends on
  `ports` + `api` only and resolves nothing through a native type directly; `command`
  holds the `@Command`-annotated, Bukkit-free subcommand handlers (`PresetCommands`,
  `AlgorithmCommands`, `MaintenanceCommands`, `ChallengeCommands`, `HelpCommands`, each a
  `CloudCommandHandler` bound via Guice's set binder in `plugin`'s `CommandModule`), driven
  entirely through Cloud's core/annotations API and the neutral `CommandSource`, with the Paper/Bukkit
  binding of the command manager (`PaperCommandSource`, `CloudCommandManagerFactory`,
  `CloudCommandRegistrar`) living in `plugin` instead of a `generator` seam.
- `plugin` (`config`): never a Bukkit edge, unlike the sibling adapters before
  rounds of framework-free cleanup; its one framework dependency is Jackson (databind plus the YAML
  and TOML dataformats) and victools for JSON Schema generation, both bundled and relocated by the
  platform. Depends on `ports` + `api` only. Holds `ConfigFormats` (per-extension mapper selection),
  `ConfigDtos` + `RuleDtos` + `ChallengeDtos` (the Jackson DTOs mirroring the on-disk shape),
  `ConfigSchemas` (the victools-generated Draft 2020-12 schema), `JacksonPresetRepository`,
  `JacksonRuleRepository`, and `JacksonChallengeRepository` (the `PresetRepository`/
  `RuleRepository`/`ChallengeRepository` implementations), and `BundledDefaults` (the seam the
  platform implements to hand over bundled defaults without this module touching Bukkit); split into
  a `config` selector, one file per preset under `presets/`, one per block under `rules/`, and one
  per challenge under `challenges/`, in JSON/YAML/TOML, plus the editor `schema/`. Jackson and
  victools here are a deliberate, permanent design choice, a cross-platform serialization/schema
  library, not a Bukkit/Paper binding, the same reasoning as Adventure's carve-out for
  `api`/`ports`/`core` (see "Enforcement (ArchUnit)" above); this module is not awaiting inversion
  into `platforms/*` the way the Bukkit edges the sibling adapters used to hold were.
- `ChallengeBehavior` (`challenge`, in `plugin`): a plain marker interface
  (no Bukkit, Guice, or jakarta.inject import). Its four concrete implementations
  (`OneLifeListener`, `NoNaturalRegenerationListener`, `NoCraftingTableListener`,
  `LevelBorderWarningTask`) live in the same package.
- `challenge.*` (a package of `plugin`): the Postgres-backed
  `ChallengeProgressRepository` implementation (`PostgresChallengeProgressRepository`), built on that
  module's JPA bootstrap (`challenge-season-system` skill).
- `plugin` (`persistence.*`): shared, reusable Jakarta Persistence (JSR 338)
  via Hibernate ORM infrastructure (`DatabaseConfig`, `Database`: the one shared database with the lazy
  `EntityManagerFactory` bootstrap, one Flyway migration history, one pool, and `read`/`inTransaction`
  helpers), depends on `ports` + `api` only. Every Postgres-backed repository (for example
  `PostgresChallengeProgressRepository` in `challenge`) takes the shared `Database` in
  its constructor instead of writing a new factory-bootstrap/transaction-lifecycle; a new one only needs
  its own `@Entity` (added to the one persistence unit), a migration in `db/migration`, and a small
  repository built on `read`/`inTransaction`.
- `plugin` (`net.onelitefeather.skygridng.*`, `net.onelitefeather.skygridng.*`): the shared Bukkit composition root
  and the single distribution's home. `SkyGridNgPlugin`, `I18n`, the Guice modules, the message
  bundles. `net.onelitefeather.skygridng` (`OlfModule`, `OlfFeature`, `ChallengeModule`, `ChallengeFeature`) is a
  naming convention, not a separate module, for mechanics specific to this OneLiteFeather server
  (challenges); `SkyGridNgPlugin` always wires `OlfModule` and enables every bound `OlfFeature`.
  `challenge` holds the four Bukkit-facing `ChallengeBehavior` implementations
  themselves. Exposes the whole graph with `api`. The only place that may use Bukkit, Guice, and
  jakarta.inject (Adventure text is allowed in every layer). A library (no shadow/plugin-yml).
- `plugin`: the single Paper distribution (thin: plugin.yml, shadow, run-paper, publishing).
  Produces `skygrid-ng-<version>.jar`. Holds the bootstrap integration tests (they need the generated
  plugin.yml).

`SkyGridNgPlugin` is extensible by design: `modules()` returns the Guice modules (a future subclass
could override and append), `injector()` exposes the graph, and
`onExtensionEnable()`/`onExtensionDisable()` run around the base lifecycle; today `SkyGridNgPlugin`
itself uses these seams directly to wire `OlfModule` and enable every bound `OlfFeature`, since there
is only one distribution.

Adding a new platform (Fabric, Sponge): create a new sibling under `platforms/` that depends on the
adapters (or a new platform-specific adapter set) and the core. `api`, `ports`, `core`, and the
existing adapters never change.

## Package map (root `net.onelitefeather.skygridng`)

The second package segment matches the module: `api.*`, `ports.*`, `core.*`, `adapters.*`,
`platforms.*`.

- `api.model` value records: `GeneratorConfig`, `SkyGridConfig`, `SpawnConfig`, `SpawnPoint`,
  `BlockWeight`, `BlockId`, `BiomeId`, plus the preset/rule records owned by
  `config-schema-and-rules` (`Preset`, `PresetCatalog`, `Rule`, `RuleCatalog`, `PlacementRule`,
  `StructurePattern`, `LegendEntry`, `Plant`, `PhysicsMode`) and the challenge records owned by
  `challenge-season-system` (`ChallengeRule`, `ChallengeCatalog`). The former Wave Function Collapse
  model (`TileType`, `FeatureSpec`, `RuleSet`, `AllowedPair`, `AdjacencyRules`, `GenerationSettings`,
  `Direction`) was removed with the `wfc` algorithm; do not reintroduce it.
- `generator.SkyGridApi`: the public read-only facade, implemented by `generator.SkyGridApiService` and
  registered as a Bukkit service in `plugin`. `generator.Seeds`: SplitMix64 derivation
  (shared by core and the generator adapter).
- `ports.in`: `RuleAdminUseCase`, `TerrainAlgorithms`, `Presets`, `Biomes`, `SkyGridDecorations`,
  `SpawnPoints`, `ChallengeAdminUseCase`. `ports.out`: `RuleRepository`, `PresetRepository`,
  `ChallengeRepository`.
  `generator`: `TerrainAlgorithm` + `ColumnSink` (the seams the generator adapter implements) +
  `RegionWriter` (read/write access to a live native generation region by `BlockId`, so decoration,
  structure, and still-water logic in `generator` stay framework-free; its Bukkit
  implementation, `LimitedRegionWriter`, lives in `plugin`, constructed fresh per generation
  call, never injected as a singleton) + `GroundReader` (read only access to a live native world's
  block state by `BlockId`, so the first-join safe-spawn search in `generator` stays
  framework-free; its Bukkit implementation, `BukkitGroundReader`, lives in `plugin`,
  constructed fresh per join event, never injected as a singleton) + `AlgorithmFactory` (Factory
  Method, one per algorithm, registered by id). The `GenerateRegionUseCase` port and the `core.solver`
  package (`WfcSolver` and its strategy interfaces) were removed along with the `wfc` algorithm.
- `generator`: `IndependentSkyGridAlgorithm` (id `skygrid`), `NoiseSkyGridAlgorithm`
  (`noise_skygrid`) - the two implementations of `TerrainAlgorithm`. `generator`: `WeightedTable`,
  `ValueNoise`.
- `generator.RuleState`: thread safe holder, Memento (undo) plus Observer (listeners).
- `core.service`: `DefaultTerrainAlgorithms`, `RuleAdminService`, `PresetService`, `PresetResolver`,
  `SkyGridApiService`, `ChallengeAdminService`, `DefaultBiomes`, `DefaultSkyGridDecorations`,
  `SpiralSpawnService` (the implementors of the in ports and the facade).
- `generator`: `DecorationEngine`, `StructurePlacer`, `StillWater`.
- `command`: `Messages` (translatable components) and `handler.CommandSource` (the neutral
  sender interface). `command`: `PresetCommands`, `AlgorithmCommands`,
  `MaintenanceCommands`, `ChallengeCommands`, `HelpCommands`, the `@Command`-annotated subcommand
  handlers, plus the platform-neutral `CloudCommandHandler` type they compile against. Enforced
  Bukkit/Paper/cloud-paper/cloud-brigadier free by an ArchUnit rule (see `references/extending.md`).
- `config`: `ConfigFormats` (JSON/YAML/TOML mapper selection by file extension),
  `ConfigDtos` + `RuleDtos` + `ChallengeDtos` (the Jackson DTOs for the preset, rule, and challenge
  files), `ConfigSchemas` (the victools-generated JSON Schema under `schema/`),
  `JacksonPresetRepository`, `JacksonRuleRepository`, and `JacksonChallengeRepository` (the
  out-port implementations), and `BundledDefaults` (the seam for the bundled default
  config/presets/rules, implemented by `plugin` so this module never touches Bukkit);
  split into a `config` selector, one file per preset under `presets/`, one per block under
  `rules/`, and one per challenge under `challenges/`. Domain of the `config-schema-and-rules` skill.
- `generator`: `SafeLocationFinder` (the first-join safe-spawn search).
- `challenge.ChallengeBehavior`: a plain marker interface (in `plugin`). Domain of the
  `challenge-season-system` skill.
- `net.onelitefeather.skygridng`: `SkyGridNgPlugin` (composition root), `I18n`, the `injection/` modules
  (`PlatformModule`, `RepositoryModule`, `GeneratorModule`, `CommandModule`), the `messages/` bundles,
  `generator` (`HexagonalChunkGenerator`, `MaterialResolver`, `BiomeResolver`,
  `BukkitIds`, `RuleResolver`, `SkyGridBiomeProvider`, `SkyGridDecorationPopulator`,
  `LimitedRegionWriter`), `generator` (`SpreadSpawnListener`, `BukkitGroundReader`
  implementing `GroundReader` over a live `World`), and `command`
  (`PaperCommandSource`, `CloudCommandManagerFactory`, `CloudCommandRegistrar`: the Paper/Cloud-paper
  binding of the `/skygrid` command manager, and the seam that parses every
  `command.CloudCommandHandler` onto it).
- `net.onelitefeather.skygridng` (inside `plugin`): `OlfModule`, `OlfFeature`, `NetworkStartupReportFeature`,
  `ChallengeModule`, `ChallengeFeature`, and its `challenge` subpackage
  (`OneLifeListener`, `NoNaturalRegenerationListener`, `NoCraftingTableListener`,
  `LevelBorderWarningTask`: the Bukkit-facing implementations of `challenge.ChallengeBehavior`).

For exact constructor and method signatures, read `references/api-surface.md` before editing. Do not
guess signatures; the records have validating compact constructors that throw
`IllegalArgumentException`, and several methods have non obvious contracts.

## Platform neutrality (no Bukkit vocabulary in the core)

The framework-free layers must be neutral semantically, not just syntactically. A missing
`org.bukkit` import is not enough if the data still speaks Bukkit. Concretely:

- A block is named by a platform-neutral, namespaced id, the `generator.BlockId` value type
  (`namespace` + `value`, lowercased and validated; `BlockId.of("stone")` defaults to `minecraft`).
  The canonical string form is `minecraft:stone`. NEVER use a Bukkit `Material` enum name
  ("STONE", "GRASS_BLOCK") in `api`, `ports`, `core`, or a `presets/`/`rules/` config file, and never
  hardcode one in an algorithm (a `TerrainAlgorithm` uses `BlockId` constants, not `"BEDROCK"`).
- The model (`BlockWeight`, `SkyGridConfig`), the `ColumnSink` port, and the weighted table
  carry `BlockId`. Resolution of a `BlockId` to a native `org.bukkit.Material` happens ONLY in
  `generator.MaterialResolver` (moved out of `generator` in the
  framework-free cleanup so the adapter never touches `Material` directly), via
  `new NamespacedKey(namespace, value)` + `Registry.MATERIAL.get(key)` (this is the WorldEdit
  `BukkitAdapter` round-trip). The Jackson DTOs in `config` hold plain `String`s for a
  block id; `JacksonPresetRepository`/`JacksonRuleRepository` call `BlockId.of(...)` at the
  boundary, there is no dedicated (de)serializer class.
- This is the EngineHub/WorldEdit and FAWE pattern: `worldedit-core` identifies blocks by namespaced
  id (`BlockType implements Keyed`), and each platform resolves that id against its own registry. The
  same id works for Bukkit, Fabric, Forge, and Sponge.

Adding a platform (Fabric, ...) is then a new adapter that resolves `BlockId` to that platform's block
type; `api`, `ports`, and `core` do not change. See the `core-platform-neutrality` memory.

## Ports, services, and registries (naming + the no-switch rule)

Ports ARE APIs - they are the typed contract for data in and out. Name a port after the domain
capability (a noun or a use-case verb phrase), and name its implementation `Default<Port>`:

- Port (in `ports/`): `TerrainAlgorithms`, `RuleAdminUseCase`.
- Implementation (in `core/service`): `DefaultTerrainAlgorithms`, `RuleAdminService` style.

Avoid `-Provider`/`-Service` as an INTERFACE suffix: `AlgorithmService implements TerrainAlgorithmProvider`
reads badly (two nouns for one concept, and `Provider` clashes with `jakarta.inject.Provider`). The
port owns the best name; `Default` (a real adjective, unlike `Impl`) marks the standard bean and
leaves room for `CachingTerrainAlgorithms` later.

Replace a growing `switch` over ids (the old `AlgorithmService` had two parallel switches for
`rebuild` and `isKnown`) with a `Registry<K, V>`: a small immutable `Map`-backed holder of named
factories keyed by a namespaced id. The registry's key set is the single source of truth for "what is
known", so `isKnown`/tab-completion/validation cannot drift. Each entry is a Factory Method (a lambda
building a Strategy from config); the registry as a whole is an Abstract Factory over the family. Keep
`Registry<K,V>` framework-free (`api`/`core`); do the registration in the Guice composition root with
`MapBinder` (jakarta.inject annotations live in the adapter/service layer, never in core). Refs:
refactoring.guru factory-method, abstract-factory, strategy. See the `generation-roadmap` memory.

## Discriminated unions

Model data that is "exactly one of N shapes" as a `sealed interface` permitting `record`s, consumed by
an exhaustive `switch` with record patterns and no `default`. The compiler then forces every consumer
to handle a new variant. Use it for the generation `Decoration` (BlockColumn|Plant|LootChest|Spawner|
Group) and config `BlockSource` (Single|Weighted). Prefer it over the GoF Visitor when the variant set
grows more often than the operations (it does here). Keep Strategy for runtime behavior families like
`TerrainAlgorithm`; use sealed unions for varying-shape data. See the `generation-pipeline` skill.

## Naming

The project was renamed from GridWFC to SkyGrid-ng. Project branded names use SkyGrid-ng
(`SkyGridNgPlugin`, `/skygrid`, permission `skygridng.admin`, i18n namespace `skygridng`). The Wave
Function Collapse method itself (`WfcSolver`, `WfcSurfaceAlgorithm`, algorithm id `wfc`) was later
removed entirely (its solid-terrain and tile-admin surface area cost more than the two remaining
void-grid algorithms needed); do not reintroduce those names or reuse `wfc` as an algorithm id. See
the `project-context` skill's decision log for the removal rationale.

## Adding things

The common extensions (a new algorithm, a new command subcommand, a new out adapter) each follow a
fixed recipe. Read `references/extending.md` and follow it step by step. The short version:

- New algorithm: add one class in `generator` implementing `generator.TerrainAlgorithm`,
  register an `AlgorithmFactory` for its id in the composition root's `MapBinder` (read by
  `DefaultTerrainAlgorithms`'s registry). Command tab completion for `/skygrid algorithm` reads
  straight from that registry (`RuleAdminUseCase.algorithmIds()`), so it updates automatically; no
  command file to touch. The generator adapter never changes.
- New command subcommand: add one `@Command`/`@Argument`/`@Permission` annotated method to an
  existing (or new) class in `command` implementing `CloudCommandHandler`, bind it
  in `command.CommandModule`'s set binder. `CloudCommandRegistrar` picks it up
  automatically; no change to the registrar or `SkyGridNgPlugin`.
- New out adapter: implement one of the existing `ports.out` interfaces (`PresetRepository`,
  `RuleRepository`, `ChallengeRepository`), in a new or existing adapter module, bind it in
  `RepositoryModule`. The core does not change.
- New OLF-only mechanic (`net.onelitefeather.skygridng` inside `plugin`): implement `OlfFeature`
  (id/enable/disable), inject the `JavaPlugin`, the public `SkyGridApi`, or any port, and add one
  `Multibinder` line in `OlfModule`. `SkyGridNgPlugin` enables it with the lifecycle. "OLF-only" is
  now a naming convention marking a mechanic as specific to this server, not a separate published
  artifact. Nothing in `api`, `ports`, `core`, or the adapters changes.

## Verifying a core change compiles

The framework-free modules (api + ports + core) compile with plain `javac`, no Gradle or network
needed. Run `scripts/compile-core.sh` after an edit for a fast sanity check before the full build.

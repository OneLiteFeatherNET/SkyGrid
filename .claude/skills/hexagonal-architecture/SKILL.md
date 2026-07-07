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
jakarta.inject, and `net.kyori.adventure.platform`); (2) adapters never depend on `core`; (3) the
layered direction holds. Adventure text (`net.kyori.adventure`) is allowed everywhere; only its
platform bindings are banned. The
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
- `adapters/generator` (`adapters.generator`): framework-free, like `core`, not Bukkit/Gson glue.
  Depends on `ports` + `api` only and resolves nothing through a native type directly; it drives
  decoration, structure, and still-water placement entirely through `ports.algorithm.RegionWriter`,
  the seam whose Bukkit implementation (`LimitedRegionWriter`, wrapping a live `LimitedRegion`) lives
  in `platforms/common` alongside the rest of the Bukkit-flavored generator classes
  (`HexagonalChunkGenerator`, `RuleResolver`, `MaterialResolver`, `BiomeResolver`).
- `adapters/spawn` (`adapters.spawn`): framework-free, like `core`, not Bukkit glue. Depends on
  `ports` + `api` only and resolves nothing through a native type directly; it drives the first-join
  safe-spawn search entirely through `ports.algorithm.GroundReader`, the seam whose Bukkit
  implementation (`BukkitGroundReader`, wrapping a live `World`) lives in `platforms/common`
  alongside the rest of the Bukkit-flavored spawn classes (`SpreadSpawnListener`).
- `adapters/command` (`adapters.command`): framework-free, like `core`, not Bukkit glue. Depends on
  `ports` + `api` only and resolves nothing through a native type directly; it drives every
  `/skygrid` subcommand entirely through Cloud's core/annotations API, with the Paper/Bukkit binding
  of the command manager (`PaperCommandSource`, `CloudCommandManagerFactory`,
  `CloudCommandRegistrar`) living in `platforms/common` instead of a `ports.algorithm` seam.
- `adapters/config` (`adapters.config`): never a Bukkit edge, unlike the sibling adapters before
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
- `adapters/challenge` (`adapters.challenge`): framework-free, like `core`, not Bukkit glue. Depends
  on `ports` + `api` only and holds nothing but `ChallengeBehavior`, a plain marker interface (no
  Bukkit, Guice, or jakarta.inject import); its four concrete implementations
  (`OneLifeListener`, `NoNaturalRegenerationListener`, `NoCraftingTableListener`,
  `LevelBorderWarningTask`) live in `platforms/common`'s `platforms.olf.challenge` package instead.
- `platforms/common` (`platforms.common.*`, `platforms.olf.*`): the shared Bukkit composition root
  and the single distribution's home. `SkyGridNgPlugin`, `I18n`, the Guice modules, the message
  bundles. `platforms.olf` (`OlfModule`, `OlfFeature`, `ChallengeModule`, `ChallengeFeature`) is a
  naming convention, not a separate module, for mechanics specific to this OneLiteFeather server
  (challenges); `SkyGridNgPlugin` always wires `OlfModule` and enables every bound `OlfFeature`.
  `platforms.olf.challenge` holds the four Bukkit-facing `ChallengeBehavior` implementations
  themselves. Exposes the whole graph with `api`. The only place that may use Bukkit, Guice, and
  jakarta.inject (Adventure text is allowed in every layer). A library (no shadow/plugin-yml).
- `platforms/paper`: the single Paper distribution (thin: plugin.yml, shadow, run-paper, publishing).
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

- `api.model` value records: `TileType`, `FeatureSpec`, `GenerationSettings`, `GeneratorConfig`,
  `RuleSet`, `SkyGridConfig`, `BlockWeight`, `AllowedPair`, `Direction`, `AdjacencyRules`.
- `api.SkyGridApi`: the public read-only facade, implemented by `core.service.SkyGridApiService` and
  registered as a Bukkit service in `platforms/common`. `api.util.Seeds`: SplitMix64 derivation
  (shared by core and the generator adapter).
- `ports.in`: `RuleAdminUseCase`, `TerrainAlgorithms`. `ports.out`: `RuleRepository`.
  `ports.algorithm`: `TerrainAlgorithm` + `ColumnSink` (the seams the generator adapter implements) +
  `RegionWriter` (read/write access to a live native generation region by `BlockId`, so decoration,
  structure, and still-water logic in `adapters.generator` stay framework-free; its Bukkit
  implementation, `LimitedRegionWriter`, lives in `platforms/common`, constructed fresh per generation
  call, never injected as a singleton) + `GroundReader` (read only access to a live native world's
  block state by `BlockId`, so the first-join safe-spawn search in `adapters.spawn` stays
  framework-free; its Bukkit implementation, `BukkitGroundReader`, lives in `platforms/common`,
  constructed fresh per join event, never injected as a singleton) + `AlgorithmFactory` (Factory
  Method, one per algorithm, registered by id). The `GenerateRegionUseCase` port and the `core.solver`
  package (`WfcSolver` and its strategy interfaces) were removed along with the `wfc` algorithm.
- `core.algorithm`: `IndependentSkyGridAlgorithm` (id `skygrid`), `NoiseSkyGridAlgorithm`
  (`noise_skygrid`) - the two implementations of `TerrainAlgorithm`. `core.util`: `WeightedTable`,
  `ValueNoise`.
- `core.state.RuleState`: thread safe holder, Memento (undo) plus Observer (listeners).
- `core.service`: `DefaultTerrainAlgorithms`, `RuleAdminService`, `SkyGridApiService` (the
  implementors of the in ports and the facade).
- `adapters.generator`: `DecorationEngine`, `StructurePlacer`, `StillWater`.
- `adapters.command`: `SkyGridCommands` (the Cloud-annotated `/skygrid` subcommands),
  `parser.SkyGridArgumentParsers` (tab-completion suggestion providers), `Messages` (translatable
  components), and `handler.CommandSource` (the neutral sender interface).
- `adapters.config`: `ConfigFormats` (JSON/YAML/TOML mapper selection by file extension),
  `ConfigDtos` + `RuleDtos` + `ChallengeDtos` (the Jackson DTOs for the preset, rule, and challenge
  files), `ConfigSchemas` (the victools-generated JSON Schema under `schema/`),
  `JacksonPresetRepository`, `JacksonRuleRepository`, and `JacksonChallengeRepository` (the
  out-port implementations), and `BundledDefaults` (the seam for the bundled default
  config/presets/rules, implemented by `platforms/common` so this module never touches Bukkit);
  split into a `config` selector, one file per preset under `presets/`, one per block under
  `rules/`, and one per challenge under `challenges/`.
- `adapters.challenge`: `ChallengeBehavior` only (a plain marker interface).
- `platforms.common`: `SkyGridNgPlugin` (composition root), `I18n`, the `injection/` modules
  (`PlatformModule`, `RepositoryModule`, `GeneratorModule`), the `messages/` bundles,
  `platforms.common.generator` (`HexagonalChunkGenerator`, `MaterialResolver`, `BiomeResolver`,
  `BukkitIds`, `RuleResolver`, `SkyGridBiomeProvider`, `SkyGridDecorationPopulator`,
  `LimitedRegionWriter`), `platforms.common.spawn` (`SpreadSpawnListener`, `BukkitGroundReader`
  implementing `GroundReader` over a live `World`), and `platforms.common.command`
  (`PaperCommandSource`, `CloudCommandManagerFactory`, `CloudCommandRegistrar`: the Paper/Cloud-paper
  binding of the `/skygrid` command manager).
- `platforms.olf` (inside `platforms/common`): `OlfModule`, `OlfFeature`, `NetworkStartupReportFeature`,
  `ChallengeModule`, `ChallengeFeature`, and its `platforms.olf.challenge` subpackage
  (`OneLifeListener`, `NoNaturalRegenerationListener`, `NoCraftingTableListener`,
  `LevelBorderWarningTask`: the Bukkit-facing implementations of `adapters.challenge.ChallengeBehavior`).

For exact constructor and method signatures, read `references/api-surface.md` before editing. Do not
guess signatures; the records have validating compact constructors that throw
`IllegalArgumentException`, and several methods have non obvious contracts.

## Platform neutrality (no Bukkit vocabulary in the core)

The framework-free layers must be neutral semantically, not just syntactically. A missing
`org.bukkit` import is not enough if the data still speaks Bukkit. Concretely:

- A block is named by a platform-neutral, namespaced id, the `api.model.BlockId` value type
  (`namespace` + `value`, lowercased and validated; `BlockId.of("stone")` defaults to `minecraft`).
  The canonical string form is `minecraft:stone`. NEVER use a Bukkit `Material` enum name
  ("STONE", "GRASS_BLOCK") in `api`, `ports`, `core`, or `generator.json`, and never hardcode one in
  an algorithm (a `TerrainAlgorithm` uses `BlockId` constants, not `"BEDROCK"`).
- The model (`BlockWeight`, `SkyGridConfig`), the `ColumnSink` port, and the weighted table
  carry `BlockId`. Resolution of a `BlockId` to a native `org.bukkit.Material` happens ONLY in
  `adapters.generator.MaterialResolver`, via `new NamespacedKey(namespace, value)` +
  `Registry.MATERIAL.get(key)` (this is the WorldEdit `BukkitAdapter` round-trip). The `BlockId`
  string mapping (plain `String` fields on the DTOs, converted via `BlockId.of(...)`) lives in
  `adapters.config`.
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

- New algorithm: add one class in `core.algorithm` implementing `ports.algorithm.TerrainAlgorithm`,
  register an `AlgorithmFactory` for its id in the composition root's `MapBinder` (read by
  `DefaultTerrainAlgorithms`'s registry), and add the id to the command tab completion. The
  generator adapter never changes.
- New out adapter: implement the existing `ports.out` interface (in a new or existing adapter
  module), bind it in `RepositoryModule`. The core does not change.
- New OLF-only mechanic (`platforms.olf` inside `platforms/common`): implement `OlfFeature`
  (id/enable/disable), inject the `JavaPlugin`, the public `SkyGridApi`, or any port, and add one
  `Multibinder` line in `OlfModule`. `SkyGridNgPlugin` enables it with the lifecycle. "OLF-only" is
  now a naming convention marking a mechanic as specific to this server, not a separate published
  artifact. Nothing in `api`, `ports`, `core`, or the adapters changes.

## Verifying a core change compiles

The framework-free modules (api + ports + core) compile with plain `javac`, no Gradle or network
needed. Run `scripts/compile-core.sh` after an edit for a fast sanity check before the full build.

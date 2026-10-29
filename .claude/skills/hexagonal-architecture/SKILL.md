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
- The composition root (root package and the plugin feature modules) is the only place that knows every
  concrete class.

## Enforcement (ArchUnit)

The `:verification` module runs ArchUnit tests (`ArchitectureTest`) on every build. They assert that the
domain classes (located by their module jar) depend on no platform or persistence framework package, that
the domain never reads the system clock, that `@Entity` classes live in the plugin module, that no code
uses `DriverManager` or a native query without a result type.
Adventure text (`net.kyori.adventure`) is allowed everywhere; only its platform bindings are banned. The
module graph already blocks most violations at compile time, but ArchUnit is the net that survives
build-file drift.

## Module layout

Two Gradle modules: `domain` (pure Java: generation logic, domain records, the public `SkyGridApi`)
and `plugin` (Paper, Guice, Jackson, JPA). The compile classpath of `domain` keeps framework code out.
`verification` runs the ArchUnit rules.

## Package map (root `net.onelitefeather.skygridng`)

- Feature packages `generator`, `challenge`, `season` exist in both modules.
- Shared plumbing in `plugin`: `persistence`, `command`, `config`.
- The root package holds `SkyGridNgPlugin`, `I18n`, the Guice modules and the OLF feature wiring.

## Platform neutrality (no Bukkit vocabulary in the domain)

The domain names blocks and biomes with `BlockId` and `BiomeId`, never Bukkit types. Plugin classes
resolve them to native types. Adventure text is allowed in the domain.

## Ports and services

Domain interfaces and their implementations sit in the same feature package. Plugin implementations
sit in the same feature package of the `plugin` module. The Guice modules wire them.

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

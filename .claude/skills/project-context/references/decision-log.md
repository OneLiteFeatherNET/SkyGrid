# Decision log

A short, ADR style record of the deliberate decisions behind SkyGrid-ng. The maintenance plan calls
for a `docs/decisions/` directory; this is the seed for it. Each entry is the decision, why, and what
it would cost to reverse. Do not reverse one of these without a stated reason.

## D1: Hexagonal architecture with a framework free core

The generation logic lives in `core/` and imports no Bukkit, Gson, Guice, jakarta.inject, or
Adventure. Why: testability without a server and survival across Paper API churn (only the adapter
layer should break when Minecraft changes). Reversing this would couple generation to the platform
and lose the unit test suite that runs without a server. This is the load bearing decision.

## D2: Target Paper 26.1 and Java 25

Paper 26.1 is the first fully deobfuscated era, which lowers breakage risk. Java 25 gives records for
the immutable domain model. Cost to reverse: large; the records and modern syntax are pervasive.

## D3: Three algorithms behind one port

Keep `wfc` for coherent adjacency based surface terrain. Add `skygrid` (classic, independent rolls)
and `noise_skygrid` (clustered via value noise) as O(1), seed based, parallel safe alternatives. All
implement `TerrainAlgorithm`; the Paper generator only knows the port. Why: speed and variety without
constraint propagation, and a fourth algorithm can be added without touching the generator. Reversing
to a single algorithm would remove the main feature.

## D4: Seed based determinism

All generation derives from the world seed via SplitMix64 in `Seeds`. WFC solves region grid lines
first (1D with anchors), then interiors against fixed borders, so seams are adjacency correct.
Verified zero adjacency violations and zero determinism deviations. Why: reproducible worlds and
seamless regions. Reversing would make worlds non reproducible and seams unreliable.

## D5: Adventure based internationalization, English code base

The core returns data, never text. The command adapter builds `Component.translatable`, and
`bootstrap/I18n` registers bundles on the Adventure `GlobalTranslator` so Paper renders per client
locale. All code is English; user facing text lives in bundles. Why: international contributors and
localized players. Reversing would hardcode language into the core.

## D6: Guice dependency injection with a single composition root

`SkyGridNgPlugin` builds the Guice graph (`Stage.PRODUCTION`) from the modules in
`bootstrap/injection`. The core has no injection annotations; its services are constructed in
`@Provides` methods. Why: centralized wiring while the core stays framework free. Reversing to manual
wiring is possible but loses the clean module seams.

## D7: AGPL-3.0 for the plugin

The plugin is AGPL-3.0; OneLiteFeather libraries use Apache-2.0. Why: copyleft for the server plugin,
permissive for shared libraries. Reversing is a licensing decision that needs maintainer and
contributor agreement.

## D8: Rename GridWFC to SkyGrid-ng, keep WFC method names

Project branding is SkyGrid-ng (`SkyGridNgPlugin`, `/skygrid`, `skygridng.admin`, i18n namespace
`skygridng`, package `net.onelitefeather.skygridng`). The WFC names (`WfcSolver`,
`WfcSurfaceAlgorithm`, algorithm id `wfc`) describe the algorithm and stay. Why: SkyGrid is the
product identity; WFC is a method inside it. Reversing the kept names would wrongly imply the project
is about WFC.

## D9: OneLiteFeather automation first standards

release-please for releases, Conventional Commits, Renovate org presets, reusable org workflows,
Crowdin, CycloneDX SBOM into Dependency-Track, CODEOWNERS teams. Why: a quiet month needs zero manual
work. Reversing increases maintenance burden. See `docs/MAINTENANCE.md`.

## D10: World generator loads at STARTUP

`plugin.yml` sets `load: STARTUP` so the generator exists before worlds load. CycloneDX 3.x config
uses `cyclonedxDirectBom` (the 3.x API moved `includeConfigs` there and removed the old setters).
Why: correctness for a world generator and a build that configures cleanly. Reverting the CycloneDX
config to the old setters breaks every Gradle invocation.

## D11 (2026-07-05): Remove the WFC algorithm and the starter platform/villager first-join spawn

Removed the `wfc` algorithm entirely: `core.solver` (`WfcSolver`, `EntropyHeuristic`,
`CollapseStrategy`, `ShannonEntropyHeuristic`, `WeightedRandomCollapse`), `WfcSurfaceAlgorithm`,
`RegionGenerationService`, and the `ports.in.GenerateRegionUseCase` port. Only `skygrid` and
`noise_skygrid` remain; an unrecognized configured algorithm id (including a leftover `"wfc"`) falls
back to `skygrid` with a warning. `SkyGridApi.tileIds()`/`tile(String)` and the `/skygrid
list|info|weight|allow|disallow` WFC tile-admin subcommands are gone too, since there is no tile
concept left to expose or administer (`API_VERSION` bumped `1.0.0` -> `2.0.0` for the breaking
change).

In the same effort, replaced the starter platform/villager first-join spawn with a safe-block-search
teleport: `SpawnConfig` dropped `platformY`/`platformBlock`/`platformRadius`/`villager` in favor of
`spread`/`spacing`/`searchRadius`/`maxAttempts`. First join still assigns the next slot in the
existing outward spiral (unchanged), but instead of building a platform and spawning a villager, it
searches near that slot for a column with solid, non-hazardous ground and passable space and
teleports the player there; a failed search falls back to a fixed high point rather than failing the
join.

Why: the WFC solid-terrain and tile-admin surface area (a dedicated solver package, a mandatory
config section, and public API surface for tile administration) cost more to maintain than the two
void-grid algorithms needed, and no shipped preset relied on it as its primary algorithm. The starter
platform/villager spawn permanently altered the generated world (a built structure plus a spawned
entity) for a mechanic that only needed a safe landing spot; the search-based teleport achieves the
same goal without leaving any trace in the world. Reversing either would reintroduce the removed
surface area (solver, tile-admin API, platform building, villager spawning) for a feature this project
decided was not worth the maintenance cost.

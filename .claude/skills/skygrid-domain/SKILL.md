---
name: skygrid-domain
description: >
  Use whenever working on what SkyGrid-ng generates or how it behaves in game: the three algorithms
  (skygrid, noise_skygrid, wfc), the generator.json config, weighted block tables, WFC tiles and
  adjacency rules, decorations, world setup, or the SkyGrid gameplay caveats and performance. It
  explains the SkyGrid concept (SethBling 2012), why each algorithm exists, and the exact config
  semantics and defaults, so a generation change matches the intended behavior instead of guessing.
  Read this before changing generation logic, config defaults, the block table, or before advising a
  server operator on configuration.
---

# SkyGrid-ng domain

This is the domain knowledge (what the plugin generates and why). For code structure and the
dependency rule, see the `hexagonal-architecture` skill. For the full operator facing config tables
and tuning advice, see `docs/ADMIN.md`.

## What SkyGrid is

SkyGrid is a classic Minecraft world type from SethBling (2012): the world is empty (void) except
for single blocks placed on a regular 3D grid, usually every 4 blocks. You hop between floating
blocks and survive from whatever the grid gives you. SkyGrid-ng revives that and adds a clustered
variant and keeps the older adjacency based surface generator.

## The three algorithms

Selected by `algorithm` in `generator.json`, switchable in game with `/skygrid algorithm <id>`. All
are seed based and deterministic: the same world seed and coordinates always produce the same result.

- `skygrid` (default, `IndependentSkyGridAlgorithm`): the classic. One block on every grid point
  (grid length `spacing`, default 4), air between, floating in the void. Each grid cell is sampled
  independently from a weighted block table using a coordinate hash. O(1) per cell, allocation light,
  parallel safe, the fastest. Faithful to the 2012 SkyGrid.
- `noise_skygrid` (`NoiseSkyGridAlgorithm`): same grid positions, but the block is chosen from a seed
  based value noise field instead of an independent roll, so similar blocks cluster into veins and
  themed regions. Still O(1) per cell. `noiseScale` controls cluster size (smaller means larger
  clusters).
- `wfc` (`WfcSurfaceAlgorithm`): the retained adjacency constrained surface generator. Solid ground
  whose surface tile comes from a Wave Function Collapse region solver with configurable tiles,
  weights, and allowed neighbor pairs. Regions are seamless (adjacency correct at seams). This is the
  only algorithm that produces solid terrain rather than a sparse void grid, and the only one that
  uses the decoration populator.

The two SkyGrid algorithms were added as fast, seed based alternatives to WFC; WFC was kept for the
coherent surface terrain option. Switching algorithm changes the terrain shape (void vs solid), so a
world should be recreated after switching rather than mixing styles.

## generator.json config

Top level: `algorithm` is `skygrid`, `noise_skygrid`, or `wfc`. The file also always carries both a
`skygrid` and a `wfc` section so switching never needs new config.

`skygrid` section (used by `skygrid` and `noise_skygrid`), shipped defaults:

- `spacing` (int, 4): grid length in blocks. Classic SkyGrid is 4.
- `minY` (int, -48) and `maxY` (int, 120): lowest and highest grid layer.
- `noiseScale` (double, 0.03): cluster frequency for `noise_skygrid` only; smaller means larger
  clusters.
- `blocks` (list): the weighted table, entries `{ "material": "minecraft:stone", "weight": 28 }`.
  Blocks are platform-neutral namespaced ids (`minecraft:stone`), not Bukkit `Material` enum names; a
  bare `stone` defaults to the `minecraft` namespace. Unknown ids fall back to a safe block (no
  crash). The adapter resolves the id to a native block (see the `core-platform-neutrality` memory).

`wfc` section: `settings` (`cellSize`, `regionSize`, `surfaceY`, `maxSolverAttempts`,
`fallbackTileId` which must be self compatible, `allowSelf`), `tiles[]` (`id`, `weight`, `neutral`,
`surface`, `fill`, `liquid`, `features[]`), and `allowed[]` neighbor pairs as `["idA","idB"]`. A WFC
tile decoration (`features[]`) has `material`, `requiresLiquidNeighbor`, `requiresGroundBlock`,
`probability`, `minHeight`, `maxHeight`. Hard limit: at most 64 WFC tile types, because the solver
domain is a 64 bit mask.

## Gameplay caveats

- `WATER` and `LAVA` sit surrounded by air, so they flow on a block update (the classic water
  elevator). Remove them from the block table to avoid flowing fluids.
- `CHEST` and `SPAWNER` are placed as plain blocks (empty chest, default spawner). Loot tables and
  spawner types are a planned feature, not yet implemented.

## Performance shape

The SkyGrid algorithms are O(1) per grid cell, allocation light, and parallel safe
(`isParallelCapable()` is true), so generation scales well. At high player counts (around 100) the
limiting factors are usually the world, not the generator: fluids, spawners and mob caps in the open
sky, entity accumulation at grid level, and region file growth. The full tuning checklist is in
`docs/ADMIN.md`.

When changing generation, keep determinism (derive everything from the seed through `Seeds`) and keep
the SkyGrid algorithms free of constraint propagation so they stay O(1).

---
name: skygrid-domain
description: >
  Use whenever working on what SkyGrid-ng generates or how it behaves in game: the two algorithms
  (skygrid, noise_skygrid), the SkyGridConfig preset fields, weighted block tables, decorations,
  world setup, the safe first-join spawn search, or the SkyGrid gameplay caveats and performance. It
  explains the SkyGrid concept (SethBling 2012), why each algorithm exists, and the exact config
  semantics and defaults, so a generation change matches the intended behavior instead of guessing.
  Read this before changing generation logic, config defaults, the block table, or before advising a
  server operator on configuration.
---

# SkyGrid-ng domain

This is the domain knowledge (what the plugin generates and why). For code structure and the
dependency rule, see the `hexagonal-architecture` skill. For the full operator facing config
reference, see `docs/README.md` (`docs/presets.md`, `docs/rules.md`); for commands and tuning
advice, see `docs/ADMIN.md`.

## What SkyGrid is

SkyGrid is a classic Minecraft world type from SethBling (2012): the world is empty (void) except
for single blocks placed on a regular 3D grid, usually every 4 blocks. You hop between floating
blocks and survive from whatever the grid gives you. SkyGrid-ng revives that and adds a clustered
variant.

## The two algorithms

Selected by a preset's `algorithm` field, switchable in game with `/skygrid algorithm <id>`. Both
are seed based and deterministic: the same world seed and coordinates always produce the same result.
An unrecognized configured id falls back to `skygrid` with a warning.

- `skygrid` (default, `IndependentSkyGridAlgorithm`): the classic. One block on every grid point
  (grid length `spacing`, default 4), air between, floating in the void. Each grid cell is sampled
  independently from a weighted block table using a coordinate hash. O(1) per cell, allocation light,
  parallel safe, the fastest. Faithful to the 2012 SkyGrid.
- `noise_skygrid` (`NoiseSkyGridAlgorithm`): same grid positions, but the block is chosen from a seed
  based value noise field instead of an independent roll, so similar blocks cluster into veins and
  themed regions. Still O(1) per cell. `noiseScale` controls cluster size (smaller means larger
  clusters).

Both algorithms produce the same sparse void grid shape, only the block choice differs, so switching
between them does not require recreating the world the way switching terrain shape would.

The Wave Function Collapse adjacency constrained surface generator (`wfc`, `WfcSurfaceAlgorithm`) was
removed: its solid-terrain and tile-admin surface area (a dedicated solver package, a mandatory
config section, and public API/`RuleAdminUseCase` tile methods) cost more to maintain than the two
void-grid algorithms needed. See `project-context` (decision log) for the removal rationale.

## SkyGridConfig fields (in a preset)

Not a standalone file: these fields live inside a preset under `presets/<name>.<ext>` (the split
config layout, `config-schema-and-rules` skill). Top level: a preset's `algorithm` is `skygrid` or
`noise_skygrid`.

`SkyGridConfig`, shipped defaults:

- `spacing` (int, 4): grid length in blocks. Classic SkyGrid is 4.
- `minY` (int, -48) and `maxY` (int, 120): lowest and highest grid layer.
- `noiseScale` (double, 0.03): cluster frequency for `noise_skygrid` only; smaller means larger
  clusters.
- `blocks` (list): the weighted table, entries `{ "material": "minecraft:stone", "weight": 28 }`.
  Blocks are platform-neutral namespaced ids (`minecraft:stone`), not Bukkit `Material` enum names; a
  bare `stone` defaults to the `minecraft` namespace. Unknown ids fall back to a safe block (no
  crash). The adapter resolves the id to a native block (see the `core-platform-neutrality` memory).
- `chestLoot`/`spawnerMobs` (list, both optional): weighted tables of loot-table ids
  (`minecraft:chests/simple_dungeon`) and mob ids (`minecraft:zombie`) for table-placed chests and
  spawners; empty means a bare chest or default spawner. See `docs/presets.md` for the full preset
  schema (biomes, spawn) and `world-generator-presets` for preset selection and inheritance.

## Safe first-join spawn

A `spawn` section (`spread`, `spacing`, `searchRadius`, `maxAttempts`) controls where a player lands
on first join, independent of the terrain algorithm. `spread: true` (default) assigns each new player
the next slot in an outward spiral, `spacing` blocks apart, so players do not land on top of each
other; a pure function of join order, so the same join order always produces the same slot sequence.
`spread: false` falls back to a single shared spawn area. Either way, first join then searches near
the assigned slot (breadth controlled by `searchRadius` and `maxAttempts`) for a column with solid,
non-hazardous ground and passable space at foot and head height, and teleports the player there; if
every attempt fails, the player is placed at a fixed high point above the slot instead of failing the
join. No block is placed and no entity is spawned as part of this search, so it works the same way
regardless of which terrain algorithm generated the column.

## Gameplay caveats

- `minecraft:water` and `minecraft:lava` sit surrounded by air, so they flow on a block update (the
  classic water elevator). Remove them from the block table to avoid flowing fluids.
- Chests and spawners get a real loot table/mob type from a preset's `chestLoot`/`spawnerMobs`
  tables; an empty table falls back to a bare chest or default spawner (see `generation-pipeline`).

## Performance shape

The SkyGrid algorithms are O(1) per grid cell, allocation light, and parallel safe
(`isParallelCapable()` is true), so generation scales well. At high player counts (around 100) the
limiting factors are usually the world, not the generator: fluids, spawners and mob caps in the open
sky, entity accumulation at grid level, and region file growth. The full tuning checklist is in
`docs/ADMIN.md`.

When changing generation, keep determinism (derive everything from the seed through `Seeds`) and keep
the SkyGrid algorithms free of constraint propagation so they stay O(1).

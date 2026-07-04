---
name: generation-pipeline
description: >
  Use whenever working on what SkyGrid-ng actually generates in the world and how the Paper adapter
  realizes it: the chunk generator, block populators, biomes, block-entities (chests/loot tables,
  spawners), entity spawns (villagers), plant placement (sugar cane near water), the End portal, and
  the multiplayer first-join spawn. Read this before fixing "chests are empty", "spawners have no
  mob", "no biomes", "no villagers / can't reach the End", "no water + sugar cane", or before adding
  any decoration to the generation. It encodes the two-stage pipeline, the Paper API recipes, and the
  rule that the core decides WHAT (deterministically) while the adapter decides HOW (Bukkit calls).
---

# Generation pipeline for SkyGrid-ng

The generator runs in two stages, and which stage you use decides what is even possible. Most
"empty chest / empty spawner / no biome" bugs come from doing the work in the wrong stage.

## The two stages

1. `HexagonalChunkGenerator.generateSurface(...)` writes into `ChunkData` (`setBlock`). This is fast
   and parallel, but `ChunkData` can only set a block's type/state. It CANNOT set block-entity data
   (a chest's loot table, a spawner's mob) and cannot spawn entities. The SkyGrid algorithms emit a
   column here through `ColumnSink`.
2. `BlockPopulator.populate(worldInfo, random, chunkX, chunkZ, LimitedRegion)` runs after, with a
   `LimitedRegion` that DOES expose block-entities and entity spawning. Everything richer than a
   plain block (loot, spawners, villagers, plants needing neighbor checks) happens here.

So: a chest or spawner placed in stage 1 is always bare. To give it content you need a populator
(stage 2). Place the block first, then decorate its block-entity.

Populators are concurrent: be thread-safe, touch only the `LimitedRegion` (never `region.getWorld()`
- it deadlocks), guard positions with `isInRegion(...)`, and use only the passed `Random` (plus
`core.util`/`api.util.Seeds`) so results stay deterministic and order-independent.

## Hexagonal split (do not break it)

The `core` decides WHAT to place, deterministically from the world seed: a `BlockId`, a loot-table
id, a mob id, a biome id, a `Decoration`. The adapter decides HOW: it resolves those ids to Bukkit
types and calls the API. No `org.bukkit` type, loot-table enum, or `EntityType` belongs in
`api`/`ports`/`core` - express them as namespaced ids / domain values and resolve in the adapter
(same rule as blocks, see the `core-platform-neutrality` memory). ArchUnit enforces this.

## Paper API recipes (adapter side, Paper 1.21.x)

- Loot chest: `region.setType(x,y,z, Material.CHEST)`, then
  `if (region.getBlockState(x,y,z) instanceof Chest c) { c.setLootTable(table); c.setSeed(seed); c.update(true,false); }`.
  Get a table from `LootTables.<NAME>.getLootTable()` or `Bukkit.getLootTable(NamespacedKey)`.
- Spawner: place `Material.SPAWNER`, then `if (... instanceof CreatureSpawner s) { s.setSpawnedType(type); s.setDelay(...); s.update(true,false); }`.
- Biomes: override `ChunkGenerator.getDefaultBiomeProvider(WorldInfo)` and return a `BiomeProvider`
  whose `getBiome(WorldInfo,x,y,z)` is a pure function of `(seed,x,y,z)` and whose `getBiomes(...)`
  lists every biome it can return. Biome drives mob spawns and tints even with decorations off.
- Entities (villager): in a populator, `region.spawn(loc, Villager.class, v -> {...})`; guard with
  `isInRegion(loc)`.
- Sugar cane: the SUPPORT block (sand/dirt/grass/...) needs WATER as a cardinal-horizontal neighbor
  at the support's own Y (not diagonal, not above/below, not relative to the cane). Cane stacks on
  top. No physics during generation, but wrong adjacency uproots on the next tick - get the geometry
  right.
- End: a void world never gets a vanilla stronghold/portal. Build a 12-block `END_PORTAL_FRAME` ring
  and `EndPortalFrame.setEye(true)` on all 12 to auto-fill the portal; the End is a separate world.
  The SkyGrid-authentic alternative is to drop frames + spawn-eggs into the block table.
- Multiplayer first-join spread: `PlayerSpawnLocationEvent` -> compute a spread cell (UUID hash on a
  grid), persist it in the player's PDC, `getChunkAtAsync(loc,true)`, `setSpawnLocation(loc)` and
  `setRespawnLocation(loc,true)`. Keep the assignment logic in a core service/port; the listener and
  PDC live in the adapter.

## Decorations as a discriminated union

Richer-than-a-block placements are modeled as a `sealed interface Decoration` permitting records
(`BlockColumn`, `Plant`, `LootChest`, `Spawner`, and a `Group` for nested ones). The core emits
`Decoration`s; the adapter consumes them with an exhaustive `switch` (no `default`), so adding a
variant fails to compile until every site handles it. Prefer this sealed-switch over a Visitor here
because the variant set grows more often than the operations. See the `hexagonal-architecture` skill
for the registry/naming conventions and the `generation-roadmap` memory for the ordered plan.

## Classic vs WFC

The classic `skygrid`/`noise_skygrid` algorithms are O(1) random tables with no features; the `wfc`
algorithm uses tiles + adjacency + a populator. When adding decorations, decide whether they apply to
all algorithms (a shared block-entity/plant populator keyed off placed blocks) or only WFC tiles, and
say so - "chests are empty" is usually because the classic algorithm has no decoration populator.

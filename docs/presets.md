# Presets

See also: [Configuration layout](configuration.md), [Per-block rules](rules.md), [overview](README.md).

A preset is a named generation profile: it decides the algorithm, the block palette, the loot, the
mobs, the biomes, the spawn, and the Wave Function Collapse (WFC) rules for a world. Presets live one
per file in `presets/`, and the file base name is the preset name (so `presets/classic.yml` is the
`classic` preset). Which preset a world uses is chosen through the generator spec (see
[Selecting a preset per world](#selecting-a-preset-per-world)).

All block, mob, loot, and biome ids are namespaced (for example `minecraft:stone`), which keeps the
config platform-neutral.

## Inheritance with `extends`

A preset may inherit from one or more other presets and override only the parts it changes. This is
the same idea as CMake presets or Minecraft world presets: state only what differs.

- `extends` accepts a single id (`extends: classic`) or a list (`extends: [classic, extra]`).
- Any field a preset omits is inherited. A field it sets overrides the inherited value.
- With several parents, they are applied **left to right**, so a later parent overrides an earlier
  one, and the preset's own fields override all parents. This is "most-derived-wins": the most
  specific value in the chain (the preset itself, then its rightmost parent, and so on) wins.

For list fields (like `blocks` or `spawnerMobs`), overriding **replaces** the whole list rather than
merging it. If a child preset sets `blocks`, it fully defines the palette for that preset; it does not
append to the parent's palette.

## Preset fields

| Field         | Type                       | Meaning |
|---------------|----------------------------|---------|
| `extends`     | id or list of ids          | Parent preset(s) to inherit from. Omit for a root preset. |
| `algorithm`   | string                     | The generation algorithm: `skygrid`, `noise_skygrid`, or `wfc`. |
| `spacing`     | integer                    | SkyGrid grid spacing (blocks between placed cells). |
| `minY`        | integer                    | Lowest grid layer. |
| `maxY`        | integer                    | Highest grid layer. |
| `noiseScale`  | number                     | Noise scale, used by `noise_skygrid` for clustering. |
| `blocks`      | list of weighted ids       | The weighted block palette. |
| `chestLoot`   | list of weighted ids       | Loot tables rolled for generated chests. |
| `spawnerMobs` | list of weighted ids       | Mobs assigned to generated spawners. |
| `biomes`      | list of biome ids          | The biome palette. |
| `spawn`       | object                     | Spread-spawn settings (see below). |
| `wfc`         | object                     | The full WFC rule set (used when `algorithm: wfc`). |

### Weighted entries (`blocks`, `chestLoot`, `spawnerMobs`)

Each entry has a namespaced `material` and a `weight`. Higher weight means the entry is picked more
often; weights are relative, not percentages.

```yaml
blocks:
  - { material: minecraft:stone, weight: 28.0 }
  - { material: minecraft:dirt, weight: 18.0 }
  - { material: minecraft:diamond_ore, weight: 1.0 }
chestLoot:
  - { material: minecraft:chests/simple_dungeon, weight: 4.0 }
spawnerMobs:
  - { material: minecraft:zombie, weight: 4.0 }
```

`chestLoot` ids are loot-table ids (for example `minecraft:chests/simple_dungeon`), and `spawnerMobs`
ids are entity ids (for example `minecraft:zombie`).

### `biomes`

A flat list of biome ids painted across the world.

```yaml
biomes:
  - minecraft:plains
  - minecraft:forest
  - minecraft:desert
```

### `spawn`

Controls the multiplayer starter spawn. Any omitted field falls back to the built-in default.

```yaml
spawn:
  spread: true                     # give each player their own spread-out spawn
  spacing: 512                     # blocks between spread spawn points
  platformY: 64                    # Y of the starter platform
  platformBlock: minecraft:cobblestone
  platformRadius: 2                # platform half-size (radius in blocks)
  villager: true                   # place a villager on the platform
```

### `wfc`

The Wave Function Collapse rule set, used only when `algorithm: wfc`. It has three parts:

- `settings`: global solver settings (`cellSize`, `regionSize`, `surfaceY`, `maxSolverAttempts`,
  `fallbackTileId`, `allowSelf`). Omitted keys keep the built-in defaults (`cellSize: 4`,
  `regionSize: 16`, `surfaceY: 64`, `maxSolverAttempts: 8`, `fallbackTileId: grass`, `allowSelf: true`).
- `tiles`: the macro tile types. Each tile has an `id`, a `weight`, a `neutral` flag, a `surface`
  block, a `fill` block, a `liquid` flag, and a list of `features` (decorations). A feature is a
  discriminated union keyed by `type`: `plant`, `loot_chest`, or `spawner`.
- `allowed`: the pairs of tile ids that may sit next to each other, each as a two-element list.

```yaml
wfc:
  settings:
    cellSize: 4
    regionSize: 16
    surfaceY: 64
    maxSolverAttempts: 8
    fallbackTileId: grass
    allowSelf: true
  tiles:
    - { id: water, weight: 6.0, neutral: true, surface: minecraft:water, fill: minecraft:sand, liquid: true, features: [] }
    - id: sugarcane
      weight: 2.5
      neutral: false
      surface: minecraft:grass_block
      fill: minecraft:dirt
      liquid: false
      features:
        - { type: plant, material: minecraft:sugar_cane, requiresLiquidNeighbor: false, requiresGroundBlock: "", probability: 0.5, minHeight: 1, maxHeight: 3, providesWater: true }
    - id: dirt
      weight: 2.0
      neutral: false
      surface: minecraft:coarse_dirt
      fill: minecraft:dirt
      liquid: false
      features:
        - { type: loot_chest, block: minecraft:chest, lootTable: minecraft:chests/simple_dungeon, probability: 0.12 }
        - { type: spawner, block: minecraft:spawner, mob: minecraft:zombie, probability: 0.06 }
  allowed:
    - [water, sand]
    - [sand, sugarcane]
    - [grass, dirt]
```

The `classic` preset ships a full WFC rule set, and because every other preset extends `classic`,
they all inherit it unless they override `wfc`.

## The three shipped presets

### `classic`

The original SkyGrid and the base every other preset builds on. It is a root preset (no `extends`),
uses the `skygrid` algorithm, and defines the full palette, loot, mobs, biomes, spawn, and WFC rules.

```yaml
# presets/classic.yml (excerpt)
algorithm: skygrid
spacing: 4
minY: -48
maxY: 120
noiseScale: 0.03
blocks:
  - { material: minecraft:stone, weight: 28.0 }
  - { material: minecraft:dirt, weight: 18.0 }
  # ... ores, logs, water, lava, chests, spawners ...
spawn:
  spread: true
  spacing: 512
  platformY: 64
  platformBlock: minecraft:cobblestone
  platformRadius: 2
  villager: true
```

### `modern`

`extends: classic` and switches to the clustering `noise_skygrid` algorithm. It restates `blocks`
(1.21-era blocks: deepslate, tuff, copper, cherry, sculk, trial spawner, vault) and `spawnerMobs`
(breeze, bogged, and friends). Everything else (spacing, Y range, loot, biomes, spawn, WFC) is
inherited from `classic`.

```yaml
# presets/modern.yml (excerpt)
extends: classic
algorithm: noise_skygrid
blocks:
  - { material: minecraft:deepslate, weight: 22.0 }
  - { material: minecraft:cherry_log, weight: 6.0 }
  - { material: minecraft:trial_spawner, weight: 0.8 }
  # ...
spawnerMobs:
  - { material: minecraft:breeze, weight: 2.0 }
  - { material: minecraft:bogged, weight: 2.0 }
  # ...
```

### `nether`

`extends: classic`, keeps the `skygrid` algorithm, and swaps in a hostile nether theme: netherrack,
blackstone, crimson/warped, ancient debris; nether mobs (blaze, wither skeleton, piglin, hoglin);
nether biomes; nether loot (`chests/nether_bridge`, `chests/bastion_treasure`); and a netherrack
starter platform with no villager.

```yaml
# presets/nether.yml (excerpt)
extends: classic
algorithm: skygrid
blocks:
  - { material: minecraft:netherrack, weight: 28.0 }
  - { material: minecraft:ancient_debris, weight: 0.5 }
  # ...
biomes:
  - minecraft:nether_wastes
  - minecraft:crimson_forest
  # ...
spawn:
  spread: true
  spacing: 512
  platformY: 64
  platformBlock: minecraft:netherrack
  platformRadius: 2
  villager: false
```

## Selecting a preset per world

Two levels of selection exist:

1. The **default preset** for the server is whatever `config.<ext>` names as `active`. A world that
   does not request a specific preset follows the default generator.
2. A **per-world preset** is chosen through the generator spec id, in the form `SkyGrid-ng:<preset>`.
   Paper passes the part after the colon as the preset id.

- `SkyGrid-ng` (no colon, or a blank id) means the default preset (the active one).
- `SkyGrid-ng:nether` selects the `nether` preset for that world.
- The match is case-insensitive.
- An unknown preset id is logged as a warning and falls back to the default preset (it never hands
  the world to the vanilla generator).

Worlds that share a preset share one generator instance, so editing that preset reaches all of them.

### Where you set the generator spec

- In `bukkit.yml` under `worlds`:

  ```yaml
  worlds:
    nether_grid:
      generator: SkyGrid-ng:nether
  ```

- With Multiverse: `/mv create <world> normal -g SkyGrid-ng:nether`.
- In code: `new WorldCreator(name).generator("SkyGrid-ng:nether")`.

If you only run one SkyGrid world, you can skip per-world selection entirely and just set `active` in
`config.<ext>`.

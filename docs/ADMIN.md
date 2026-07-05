# Admin and Owner Guide

Everything a server owner can configure in SkyGrid-ng, plus tuning advice for large SkyGrid servers
(around 100 players).

## Installing and creating a world

1. Drop `skygrid-ng.jar` into `plugins/` and start the server once. This writes
   `plugins/SkyGrid-ng/generator.json`.
2. Tell the server to use the generator for a world, in `bukkit.yml`:

   ```yaml
   worlds:
     skygrid:
       generator: SkyGrid-ng
   ```

   Or with a multiworld manager, pass the generator id `SkyGrid-ng` when creating the world.
3. Restart or load the world. The End environment is not supported as a void generator target.

## Commands

Permission node: `skygridng.admin` (default: operators). All messages are localized to the
player's client language.

| Command | Description |
|---------|-------------|
| `/skygrid algorithm [id]` | Show or switch the active algorithm (`skygrid`, `noise_skygrid`). |
| `/skygrid skyweight <material> <value>` | Set a SkyGrid block weight (adds the material if absent). |
| `/skygrid undo` | Revert the last change. |
| `/skygrid reload` | Reload `generator.json`, clear caches. |
| `/skygrid save` | Write the current state back to `generator.json`. |

Runtime changes affect newly generated chunks only. Switching the algorithm changes the terrain
shape (void vs solid), so recreate the world after switching rather than mixing styles.

## generator.json reference

### Top level

| Key | Type | Meaning |
|-----|------|---------|
| `algorithm` | string | `skygrid` or `noise_skygrid`. |

### `skygrid` (used by `skygrid` and `noise_skygrid`)

| Key | Type | Default | Meaning |
|-----|------|---------|---------|
| `spacing` | int | 4 | Grid length in blocks; classic SkyGrid is 4. |
| `minY` | int | -48 | Lowest grid layer. |
| `maxY` | int | 120 | Highest grid layer. |
| `noiseScale` | double | 0.03 | Cluster frequency for `noise_skygrid`; smaller means larger clusters. |
| `blocks` | list | see file | Weighted block table: `{ "material": "STONE", "weight": 28 }`. |

Material names are Bukkit names. Unknown names fall back to a safe block instead of crashing.

## SkyGrid caveats

- `WATER` and `LAVA` sit surrounded by air, so they can flow on a block update (the classic
  "water elevator" issue). Remove them from the block table if you do not want flowing fluids.
- `CHEST` and `SPAWNER` are placed as plain blocks (empty chest, default spawner). Loot and
  spawner types are a planned feature.

## Performance tuning for ~100 players

The SkyGrid algorithms are O(1) per grid cell, allocation light, and parallel safe, so chunk
generation itself scales well. The limiting factors at high player counts are usually the world,
not the generator. Recommended baseline:

- **View distance.** Lower `view-distance` (server.properties) to 6 to 8 and set
  `simulation-distance` to 4 to 6. SkyGrid is sparse, so a smaller simulation distance has little
  visual cost and large CPU savings.
- **Fluids.** Prefer removing `WATER`/`LAVA` from the block table on busy servers; flowing fluids
  cause continuous block updates and lighting work across thousands of isolated sources.
- **Spawners and mobs.** Keep `SPAWNER` weight low. With 100 players spread across a void world,
  many active spawners plus the open sky (high mob cap headroom) can dominate the tick. Tune
  `spigot.yml` mob spawn limits and `paper-world-defaults.yml` per player mob spawns.
- **Entity activation and collisions.** Use Paper's entity activation range and
  `max-entity-collisions` defaults or lower; void worlds let entities accumulate at grid level.
- **Region files.** Void chunks are cheap but still stored. Expect the region folder to grow as
  players explore; budget disk and consider a world border (for example 30000 blocks) to bound it.
- **Async chunk loading.** Keep Paper's async chunk system on. The generator sets
  `isParallelCapable() = true`, so generation uses worker threads.
- **No tick view distance.** Consider Paper's `no-tick-view-distance` so players see far without
  ticking those chunks.
- **Folia.** For very high concurrency, a Folia compatible build (regionized threading) is on the
  roadmap; see `docs/MAINTENANCE.md`.

Always load test with a population tool before opening to 100 real players, and watch
`/timings` or `/spark` for the actual hotspots on your hardware and plugin mix.

## Internationalization

Messages ship for English and German. To add a language, add
`src/main/resources/messages/Bundle_<locale>.properties` and register the locale in
`bootstrap/I18n`, or translate through Crowdin. Item display names are not translated by Paper's
GlobalTranslator (a platform limitation).

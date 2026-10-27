# Admin and Owner Guide

Everything a server owner can configure in SkyGrid-ng, plus tuning advice for large SkyGrid servers
(around 100 players).

## Installing and creating a world

1. Drop `skygrid-ng.jar` into `plugins/` and start the server once. This seeds
   `plugins/SkyGrid-ng/` with the bundled default `config.yml`, `presets/`, and `rules/` (see
   [docs/README.md](README.md) for the full configuration reference).
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
| `/skygrid help [subcommand]` | List every subcommand, or show focused usage for one. |
| `/skygrid preset <name>` | Switch the active preset. |
| `/skygrid presets` | List known presets and the active one. |
| `/skygrid worlds` | List per-world preset assignments. |
| `/skygrid algorithm [id]` | Show or switch the active algorithm (`skygrid`, `noise_skygrid`). |
| `/skygrid skyweight <material> <value>` | Set a SkyGrid block weight (adds the material if absent). |
| `/skygrid reload` / `/skygrid reload confirm` | Reload the config from disk; discards the undo history, so it asks for confirmation first. |
| `/skygrid save` | Flatten the current (possibly in game edited) state back into the active preset file. |
| `/skygrid undo` | Revert the last change. |
| `/skygrid challenge list` | List challenge rules and whether each is active. |
| `/skygrid challenge toggle <id>` | Enable or disable a challenge rule. |
| `/skygrid challenge tier [player]` | Show your own (or another player's) challenge tier progress. |
| `/skygrid challenge tier reset` / `... reset confirm` | Reset every player's challenge tier progress; asks for confirmation first. |

Runtime changes affect newly generated chunks only. Switching the algorithm changes the terrain
shape (void vs solid), so recreate the world after switching rather than mixing styles.

## Configuration reference

The config lives in `plugins/SkyGrid-ng/`, split into a `config.<ext>` selector, one file per preset
under `presets/`, and one file per block under `rules/` (JSON, YAML, or TOML, chosen per file by
extension). See [docs/README.md](README.md) for the full reference:

- [Configuration layout and formats](configuration.md): the split layout, the three formats, and
  the generated editor `schema/`.
- [Presets](presets.md): the preset schema (`algorithm`, `spacing`/`minY`/`maxY`/`noiseScale`, the
  weighted block table, chest loot and spawner mob tables, biomes, spawn), preset inheritance
  (`extends`), and the shipped presets (`classic`, `standard`, `nether`).
- [Per-block rules](rules.md): the per-block `state`/`physics`/`placement` rule model.

Block ids are platform-neutral namespaced ids (`minecraft:stone`), not Bukkit `Material` enum names;
a bare `stone` defaults to the `minecraft` namespace. Unknown ids fall back to a safe block instead
of crashing.

## Database

Challenge tiers and task progress are stored in PostgreSQL. The connection comes from environment
variables only, never from a config file:

- `SKYGRID_DB_HOST`: the database host, default `localhost`.
- `SKYGRID_DB_PORT`: the database port, default `5432`.
- `SKYGRID_DB_NAME`: the database name, default `skygridng`.
- `SKYGRID_DB_USER`: the database user. Required, no default.
- `SKYGRID_DB_PASSWORD`: the database password. Required, no default.

The connection is opened lazily, on the first use of a store, not when the server starts. On that
first connection Flyway migrates the schema automatically, so the database user needs `CREATE`
rights on the `public` schema of that database. Hibernate only validates the schema and never
changes it.

## SkyGrid caveats

- `minecraft:water` and `minecraft:lava` sit surrounded by air, so they can flow on a block update
  (the classic "water elevator" issue). Remove them from the block table if you do not want flowing
  fluids.
- Chests and spawners placed by a preset's `chestLoot`/`spawnerMobs` tables get a real loot table or
  mob type; an empty `chestLoot`/`spawnerMobs` table falls back to a bare chest or default spawner.

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

Messages ship for English and German, written as MiniMessage (color, click, hover) rather than plain
text. To add a language, add `platforms/common/src/main/resources/messages/Bundle_<locale>.properties`
and register the locale in `platforms.common.I18n`; the German bundle flows through Crowdin and is
never hand-edited directly. Item display names are not translated by Paper's `GlobalTranslator` (a
platform limitation).

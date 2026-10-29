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

Season progress uses the same database. Before you enable seasons on an existing server, take a
backup first; see [Before the cut-over](#before-the-cut-over).

## Seasons

Seasons are optional. Without any file in `seasons/`, the server runs one continuous world, the
implicit `legacy` season, exactly as it did before. No season is active, the season scheduler does not
start, and `/skygrid season info` reports that no season is configured.

With seasons, the server runs a series of fixed-length windows. Each season has its own world, its own
preset, and its own set of challenges. When a season ends, the players' vanilla state is reset, the
season world is archived, and the next season's world takes over. Tier and task history are kept per
season.

### Turning seasons on

1. Start the server once. This writes `plugins/SkyGrid-ng/seasons/example.yml.disabled`. The `.disabled`
   suffix means it is not loaded.
2. Copy it to `seasons/<id>.yml`, for example `seasons/s01_overgrowth.yml`, and edit the copy. The file
   name is for humans; the `id` inside the file is what counts. Supported extensions are `.yml`,
   `.yaml`, `.json` and `.toml`.

| Field | Meaning |
|-------|---------|
| `id` | Required. Lowercase letters, digits and underscores, up to 64 characters. It names the season in the database and the world (`skygridng:season_<id>`). |
| `displayName` | Required. The season name, in MiniMessage (`<green>Season 1</green>`). |
| `start` | Required. An ISO-8601 instant, for example `2026-12-01T18:00:00Z`. Use UTC. |
| `duration` | Required. A positive, fixed length: an ISO-8601 duration (`PT48H`) or whole weeks (`P8W`). |
| `preset` | Required. A preset name from `presets/`. |
| `challenges` | A list of challenge ids from `challenges/`. Only these are active during the season. May be empty. |
| `seed` | Optional. A fixed world seed. Left out, a random seed is drawn when the world is prepared. |

The rules, checked when the server starts and on every reload:

- A season that names an unknown preset or challenge is rejected, and the reason is logged. The other
  seasons still load.
- Two seasons must not overlap. The one that starts later is rejected.

The first season starts from the legacy world. The implicit `legacy` season is the main world (the
`level-name` world). Its world is prepared `prepareLead` before the first season's start. At that start,
everyone in the main world is moved into the season world and reset. If the server starts while a
season is already running, it prepares and switches at once.

#### The `season` block in `config.yml`

The block is optional. Every key falls back to its default when it is left out.

```yaml
season:
  prepareLead: PT15M       # default PT15M: how long before a boundary the next world is created
  archive:
    directory: archive     # default archive: relative to the server root, or an absolute path
  advance:
    minNotice: PT24H       # default PT24H: the least notice of a scheduled season end
```

`archive.compress` is not supported. A key left in a season file or in `config.yml` is ignored.

### The void lobby

Season worlds are generated by SkyGrid-ng, so the `level-name` world, which is the main world and the
place players wait in, should be a small void lobby. Give it a generator other than `SkyGrid-ng` in
`bukkit.yml`:

```yaml
worlds:
  world:                   # the level-name world from server.properties
    generator: <a void generator>
```

Season worlds are not listed in `bukkit.yml`. They are created with the season's preset and live in
`<level>/dimensions/skygridng/season_<id>/`. The plugin does not archive the `legacy` world itself. If
you keep the old SkyGrid world, copy it by hand, before the cut-over, into the archive directory.

### Commands

All season commands are under `/skygrid season`. Commands that change state need `skygridng.admin`
and their `confirm` form. The unconfirmed form only prints a warning, so a mistyped command changes
nothing.

| Command | Permission | Effect |
|---------|------------|--------|
| `season info` | none | The active season: name, end date, what is deleted and kept. |
| `season history [player]` | none for yourself, `skygridng.admin` for another player | Tier per season. |
| `season list` | `skygridng.admin` | Every configured season, with its window. |
| `season status` | `skygridng.admin` | The phase, the next season, and when the next step is due. |
| `season advance` | `skygridng.admin` | Warning only. |
| `season advance confirm` | `skygridng.admin` | Schedules the end of the active season at least `minNotice` from now (default 24 hours), and sends the notices for it. Refused when no season follows. |
| `season advance now` | `skygridng.admin` | Warning only. |
| `season advance now confirm` | `skygridng.admin` | Ends the active season at once, without notice. The acting admin and the time are logged. Refused when no season follows. |
| `season postpone <duration>` | `skygridng.admin` | Warning only. |
| `season postpone <duration> confirm` | `skygridng.admin` | Moves the season end by the duration (`30m`, `2h` or `3d`) and announces the new end. |
| `season retry` | `skygridng.admin` | Retries a failed world creation or archive. Takes no `confirm`. |
| `season reload` | `skygridng.admin` | Warning only. |
| `season reload confirm` | `skygridng.admin` | Re-reads the season files. The active season keeps its window; only later seasons change. |

Use `advance confirm` for a planned end. The notices can go out before it. Use `advance now confirm`
only when you must end the season immediately; the players get no notice.

Failed world creation is not retried automatically. The admins get an alert, `season status` shows the
phase, and `season retry` starts the step again.

### What players lose and keep

A player is reset on the first entry into a new season. The reset clears inventory, ender chest,
experience, health, hunger, potion effects and the respawn point, and sets survival mode. It keeps the
tier and task history and the archived world. Each player is reset once per season.

Announcements go to every online player, in the player's own language, with the absolute end date
(in UTC) and the deleted and kept lists:

- when the season starts,
- 7 days, 24 hours, 1 hour and 5 minutes before a season end,
- when `season postpone ... confirm` moves the end (the new end date only).

A player who joins during the last seven days of a season gets one line with the end date, so a late
joiner is not surprised by the reset.

### Before the cut-over

Back up the database before the first release that contains seasons. The progress migrations replace
the primary keys of the tier and task tables and move every existing row into season `legacy`. The
new `season_run` table is created as well. The previous release cannot read the new columns, so
restoring the backup is the only way back.

### Archive and the external map renderer

When a season ends and its world is unloaded, its folder moves to `<archive.directory>/season_<id>/`,
with a `season.json` file that holds the season id, the world key, the seed, and the start and end
instants. The archive directory is the hand-off point for an external map renderer, such as BlueMap.
Point the renderer at it, read only. Never let the renderer write into the archive.

If a folder with the same name already exists in the archive, the archive step fails and the admins
are alerted. Check the directory, then run `season retry`.

### CloudNet

Run the SkyGrid service as a static service with a persistent `world/` folder (which holds the season
worlds under `dimensions/skygridng/`) and a persistent archive directory. A dynamic service is rebuilt
from its template on every start, which would drop both. Never sync the world or the archive back into
a template. The database is authoritative for the active season and its seed.

### Known limits

- Dates are shown in UTC, not in the player's time zone.
- The season scheduler runs every 30 seconds, so a boundary can be acted on up to 30 seconds late.
- A player who joins in the first seconds after a restart waits in the world the server gave them, and
  is moved into the active season once it is known.
- Failed world creation and failed archives are not retried automatically; use `season retry`.
- If no season follows the active one at its end, the active season keeps running and the admins get
  a daily warning. A restart may repeat that warning once.
- One SkyGrid service per database. Two servers sharing a database are not supported.

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

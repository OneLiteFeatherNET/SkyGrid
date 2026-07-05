# SkyGrid-ng configuration

SkyGrid-ng generates a floating grid of blocks (the SkyGrid) and decorates it with loot chests,
spawners, biomes, plants, and a starter spawn. What it generates is driven entirely by configuration
files that live in the plugin's data folder (`plugins/SkyGrid-ng/`). This section documents that
configuration for server admins and map makers.

The configuration is split into three concerns:

- A tiny **selector** file (`config.<ext>`) that only names the active preset.
- A **`presets/`** directory with one file per preset. A preset is a complete generation profile
  (algorithm, palette, loot, mobs, biomes, spawn) and may inherit from other presets.
- A **`rules/`** directory with one file per block. A rule pins a block's data state, its physics
  behavior, and, for decorations like sugar cane, its placement conditions.

JSON, YAML, and TOML are all accepted. The format is chosen per file from its extension, so you can
mix formats freely (a YAML preset next to a TOML rule).

Everything is deterministic: the same world seed always produces the same world, so a config change
only changes newly generated chunks, never the seed-to-world mapping.

## Pages

- [Configuration layout and formats](configuration.md): the split file layout, the three formats,
  the `schema/` directory, and editor validation/autocompletion.
- [Presets](presets.md): the preset schema (algorithm, palette, loot, mobs, biomes, spawn),
  inheritance (`extends`), the three shipped presets (`classic`, `standard`, `nether`), and
  selecting a preset per world.
- [Per-block rules](rules.md): the rule model, `state`, `physics`, `placement`, water modes, and
  worked examples from the shipped defaults.

## At a glance

```
plugins/SkyGrid-ng/
  config.yml            # selects the active preset
  presets/
    classic.yml         # the base preset every other one builds on
    standard.yml        # extends: classic
    nether.yml          # extends: classic
  rules/
    oak_leaves.yml      # per-block rules
    cherry_leaves.yml
    sugar_cane.yml
    water.yml
  schema/               # generated JSON Schema files for editor validation
```

On first run the data folder is empty, so SkyGrid-ng seeds it with the bundled defaults shown above.
Edit the files, then reload or restart, or edit in game with `/skygrid preset <name>` (saving in game
rewrites the files, and comments are not kept).

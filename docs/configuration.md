# Configuration layout and formats

See also: [Presets](presets.md), [Per-block rules](rules.md), [overview](README.md).

SkyGrid-ng keeps its configuration in the plugin data folder (`plugins/SkyGrid-ng/`). The layout is
split so each file has one job, and every file is small and readable.

## The split layout

```
plugins/SkyGrid-ng/
  config.yml            # selector: only names the active preset
  presets/              # one file per preset
    classic.yml
    modern.yml
    nether.yml
  rules/                # one file per block
    oak_leaves.yml
    cherry_leaves.yml
    sugar_cane.yml
    water.yml
  schema/               # generated JSON Schema files (for editors)
```

### The `config.<ext>` selector

`config` is deliberately tiny. It holds a single field, `active`, which names the preset to generate
with. Its value must match a file base name in `presets/`.

```yaml
# config.yml
active: classic
```

That is the whole file. The presets themselves live in `presets/`, so switching the world profile is
a one-line change (or `/skygrid preset <name>` in game). If `active` is omitted, the first preset in
alphabetical order is used.

### The `presets/` directory

One file per preset. The preset's name is its file base name, not a field inside the file: a preset
in `presets/classic.yml` is named `classic`. See [Presets](presets.md) for the full schema and
inheritance rules.

### The `rules/` directory

One file per block. The block id inside the file is authoritative; the file name is only there for
humans (so `rules/oak_leaves.yml` and `rules/oak-leaves.yml` would both be read, and the `block:`
field decides which block the rule governs). See [Per-block rules](rules.md) for the model.

## The three formats

JSON, YAML, and TOML are all supported. The format is chosen **per file from its extension**, so the
loader never guesses: it reads the extension and picks the matching parser.

| Extension        | Format |
|------------------|--------|
| `.json`          | JSON   |
| `.yml`, `.yaml`  | YAML   |
| `.toml`          | TOML   |

Because the choice is per file, you can mix formats. A YAML preset can sit next to a TOML rule next
to a JSON `config`. To convert a file, rename it to the new extension and rewrite its contents in
that syntax (there is no auto-conversion of an existing file's contents on rename).

The parsers are configured leniently in three ways that matter in practice:

- **A scalar is accepted where a list is expected.** For inheritance you may write either
  `extends: classic` or `extends: [classic]`; both mean the same thing.
- **Unknown keys are ignored.** A newer config with a key an older build does not know about still
  loads, rather than failing.
- **Omitted keys mean "inherit" or "use the default".** A missing field in a preset inherits from its
  parent (or the built-in default at the root); a missing field in a rule falls back to the rule's
  neutral default. On save, inherited (unset) fields are not written back, so the files stay minimal.

The shipped defaults are all YAML. The default extension used when SkyGrid-ng creates a new file (for
example when saving a preset that did not exist on disk) is `.yml`. When it rewrites an existing file,
it keeps that file's format.

## Editing in game

`/skygrid preset <name>` switches the active preset at runtime. Editing and saving in game rewrites
these files. Note that a rewrite does not preserve comments, so if you keep explanatory comments in
your files, prefer editing the files directly and reloading.

## Schema, validation, and autocompletion

The on-disk schema mirrors a set of internal data-transfer objects one to one, and SkyGrid-ng ships a
JSON Schema (Draft 2020-12) generated from those types into the `schema/` directory. Two things use
it:

- The plugin can validate a loaded config against the schema, so a typo in a key or a wrong value
  type is reported clearly instead of silently ignored.
- Your editor can use the same schema files for **inline validation and autocompletion** while you
  edit presets and rules.

### Pointing an editor at the schema

Most editors with a YAML/JSON language server (for example VS Code with the YAML and JSON
extensions) let you associate a schema with a set of files by glob. Point the preset schema at
`presets/*` and the rule schema at `rules/*`.

A VS Code `settings.json` example (adjust the paths to your server's data folder):

```jsonc
{
  "yaml.schemas": {
    "plugins/SkyGrid-ng/schema/preset.schema.json": "plugins/SkyGrid-ng/presets/*.yml",
    "plugins/SkyGrid-ng/schema/rule.schema.json": "plugins/SkyGrid-ng/rules/*.yml",
    "plugins/SkyGrid-ng/schema/config.schema.json": "plugins/SkyGrid-ng/config.yml"
  }
}
```

For JSON files you can also add a `$schema` key at the top of the file pointing at the matching
schema, and the editor will validate against it directly.

Because the schema is generated from the same types the loader uses, autocompletion suggests exactly
the keys the loader understands, and validation flags anything it would ignore.

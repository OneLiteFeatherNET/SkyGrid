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
    standard.yml
    nether.yml
  rules/                # one file per block
    oak_leaves.yml
    cherry_leaves.yml
    sugar_cane.yml
    water.yml
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

## The file format

Config files are YAML. Both `.yml` and `.yaml` are read and written; the two extensions are equivalent.

JSON and TOML are no longer supported. A `.json` or `.toml` file in `presets/`, `rules/`, `challenges/`
or `seasons/`, or a `config.json` or `config.toml`, is skipped, and the server logs one warning per file.
To convert such a file, rewrite its contents as YAML and rename it to `.yml`.

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


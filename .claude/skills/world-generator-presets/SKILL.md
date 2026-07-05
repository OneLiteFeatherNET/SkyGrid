---
name: world-generator-presets
description: >-
  Explains how SkyGrid-ng assigns a generation preset to each world through the
  generator specification (the `SkyGrid-ng:<preset>` id) and how presets inherit
  from one another. Use when working on per-world generators, the
  getDefaultWorldGenerator id parameter, bukkit.yml generator settings,
  Multiverse world creation, WorldCreator.generator, preset inheritance/extends,
  the PresetResolver/PresetCatalog/PresetService, or assigning classic/standard/
  nether (or any preset) to a specific world on a multi-world server.
---

# Per-world generator presets

A SkyGrid-ng preset is a complete generation profile (see the `skygrid-domain`
and `generation-pipeline` skills for what gets generated). This skill is about
**which preset a world uses** and **how presets inherit**.

## How a per-world preset reaches the plugin (the non-obvious part)

Paper calls `JavaPlugin.getDefaultWorldGenerator(String worldName, String id)`
once per world at load. The `id` is **the text after the colon in the generator
name** `SkyGrid-ng:<id>`. It is NOT Bukkit's `generator-settings` (that field is
a vanilla-only JSON used by `WorldType.FLAT` and never reaches the plugin).

An admin selects a preset per world by writing `SkyGrid-ng:<preset>` wherever a
generator is specified:

- **bukkit.yml** (binds a generator to a world the server already loads; it does
  NOT create worlds):
  ```yaml
  worlds:
    skygrid_standard:
      generator: SkyGrid-ng:standard
  ```
- **Multiverse** (current docs use the long flag; old 2.x/4.x used `-g`):
  ```
  /mv create skygrid_standard normal --generator SkyGrid-ng:standard
  ```
- **Programmatic** (`WorldCreator`): `new WorldCreator("w").generator("SkyGrid-ng:standard")`.
  Never use `WorldCreator.generatorSettings(...)` for this; it is vanilla-only.

Rules to honor in the adapter:
- A blank or missing id (just `SkyGrid-ng`) means "use the default preset" (the
  catalog's `active`).
- An unknown id: log a clear warning naming the world and the available presets,
  then fall back to the default preset. Do NOT return `null` (Paper then silently
  uses the vanilla generator, a confusing failure for a SkyGrid world).
- The id selects WHICH preset; it never injects randomness. Generation stays
  deterministic from the world seed plus the resolved preset.
- Parse and validate the id in the Paper adapter only; the core receives an
  already-resolved preset. Keep colon-parsing and Bukkit logging out of the core.

Sibling method with the same id semantics, if per-world biomes ever need it:
`getDefaultBiomeProvider(String worldName, String id)`.

## Where presets live in the code

- `api.model.Preset`: one named preset; `extends` declares its parent(s); any
  field left null is inherited. `api.model.PresetCatalog`: the presets map plus
  the default `active`. (`api`, framework-free.)
- `core.service.PresetResolver`: flattens a preset's inheritance into a complete
  `GeneratorConfig`, rejecting cycles, unknown parents and missing essentials.
  Resolution is the core's job; the adapter never resolves.
- `core.service.PresetService`: owns the live catalog, applies presets, and
  absorbs reload/save from the admin service.
- `adapters.config.ConfiguratePresetRepository`: maps the raw preset catalog to
  and from YAML/HOCON (`generator.yaml` / `generator.conf`).
- The bundled default ships `classic` (the base), `standard` and `nether`.

## Preset inheritance (single and multiple)

A preset overrides only the fields it sets; the rest is inherited. The most
derived preset wins (the model used by CMake presets and Minecraft world
presets). `extends` is a single id or a list. `PresetResolver` flattens the
whole graph with a post-order walk (a shared base in a diamond is applied
once) and detects cycles across the graph. With several parents they apply
left to right, so a later parent overrides an earlier one, and the preset's
own fields win over all.

```yaml
presets:
  classic:  { algorithm: skygrid, blocks: [...], spawn: {...}, biomes: [...] }
  standard: { extends: classic, blocks: [...], spawnerMobs: [...] }   # overrides only these
  hybrid:   { extends: [standard, nether] }                           # nether wins on conflicts
active: classic
```

## Runtime commands and what is live

- `/skygrid preset <name>` sets the active (default) preset: it is both the
  default for no-id worlds and the target the edit commands (weight, allow,
  algorithm, ...) act on.
- `/skygrid worlds` lists which preset each world was assigned.
- Editing the active preset and `/skygrid save` re-resolves every per-preset
  live state, so the edit reaches all worlds that use that preset or derive
  from it. This is the "live from a shared catalog" behaviour.
- Bukkit binds a world's generator at world load, so you CANNOT swap a loaded
  world's preset at runtime. To give a world a different preset, change its
  generator spec and reload/recreate the world (e.g. via Multiverse). The
  per-world assignment lives in the generator spec, not in a `/skygrid` toggle.

## Validate a change

- Fast type check of api/ports/core: run
  `.claude/skills/hexagonal-architecture/scripts/compile-core.sh`.
- Resolution logic: `./gradlew :core:test --tests "*PresetResolverTest"`.
- Config parsing (YAML + HOCON parity): `./gradlew :adapters:config:test`.
- A per-world generator assignment cannot be proven by MockBukkit (it does not
  drive `getDefaultWorldGenerator` with an id); smoke-test on real Paper with
  `./gradlew :platforms:paper:runServer` and a world configured as
  `SkyGrid-ng:<preset>`, then confirm that world generates that preset.

## Hard rules (do not break)

- Blocks, mobs, biomes are namespaced ids (`minecraft:stone`), never a Bukkit
  `Material` name, in `api`/`ports`/`core` or the config; resolve to native types
  only in the adapter.
- The dependency direction stays `platforms -> adapters -> ports -> api` and
  `core -> ports -> api`. Per-world wiring lives in the Paper adapter and the
  composition root, never in the core.
- Always use the term "preset" (not "profile" or "config set"). English only, no
  em dashes.

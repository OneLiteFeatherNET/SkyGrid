---
name: config-schema-and-rules
description: >-
  Explains the SkyGrid-ng on-disk configuration system and the per-block rule model: the split layout
  (a config selector, one file per preset under presets/, one file per block under rules/, generated
  JSON Schemas under schema/), the JSON/YAML/TOML per-file format choice, the Jackson DTO to neutral
  record mapping in adapters/config, and the state/physics/placement rule shape. Use when editing
  config.yml/json/toml, a presets/<name> or rules/<block> file, ConfigDtos, RuleDtos,
  ConfigFormats, JacksonPresetRepository or JacksonRuleRepository; when adding a config format,
  preset field or rule field; when working on block state, PhysicsMode, PlacementRule,
  StructurePattern/LegendEntry or RuleResolver; or when answering water/waterlogged, leaf decay,
  falling sand or "flows into the void" config questions. Cross-links skygrid-domain (generation
  semantics) and world-generator-presets (preset selection and inheritance), which own those topics.
---

# Config schema and per-block rules

This skill owns the **config file system** (layout, formats, the DTO to record mapping) and the
**per-block rule model** (state, physics, placement). It does not own:

- what the generation values mean (block weights, WFC tiles, biomes, spawn): see `skygrid-domain`.
- which preset a world uses and how presets inherit via `extends`: see `world-generator-presets`.
- how a rule is realized as Bukkit blocks at generation time: see `generation-pipeline`.

Cross-link those; do not restate them here.

## Table of contents

- [The split layout](#the-split-layout)
- [Formats: one per file, chosen by extension](#formats-one-per-file-chosen-by-extension)
- [The per-block rule model](#the-per-block-rule-model)
- [The structure pattern: a 3D footprint per decoration](#the-structure-pattern-a-3d-footprint-per-decoration)
- [Determinism](#determinism)
- [How a change flows through the layers](#how-a-change-flows-through-the-layers)
- [A rule applies everywhere its block is generated](#a-rule-applies-everywhere-its-block-is-generated)
- [Where to look](#where-to-look)

## The split layout

The whole config lives in the plugin data folder and is split into four parts. First run seeds each
from the bundled defaults under `adapters/config/src/main/resources/`.

- `config.<ext>` (the selector). Only names the active preset: `active: classic`. Nothing else.
  Mapped by `ConfigDtos.ConfigDto(String active)`.
- `presets/` (one file per preset, **file base name = preset name**). `classic.yml`, `modern.yml`,
  `nether.yml`. The preset name is deliberately not a field inside the file; it comes from the file
  name. A preset may `extends` one or more parents and override only what it changes. Mapped by
  `ConfigDtos.PresetDto`. Seeded from `presets/classic.yml`, `presets/modern.yml`, `presets/nether.yml`.
- `rules/` (one file per block, **the `block:` id inside the file is authoritative**; the file name
  is only for humans). `oak_leaves.yml`, `cherry_leaves.yml`, `sugar_cane.yml`, `water.yml`. Mapped by
  `RuleDtos.RuleDto`. Seeded from the matching resources.
- `schema/` (generated JSON Schemas, Draft 2020-12, for editor validation only). These validate the
  config in an IDE and are not read at runtime. Treat them as generated output, not a hand-edited
  source of truth.

The two out adapters are `JacksonPresetRepository` (config selector + presets) and
`JacksonRuleRepository` (rules). Both seed defaults if absent, load every supported file in the
directory, and on save keep an existing file's format or default to `.yml`. Editing in game and
saving rewrites these files and does not keep comments.

## Formats: one per file, chosen by extension

JSON, YAML and TOML are all accepted and can be mixed. The format is chosen **per file from its
extension** in `ConfigFormats.mapperFor(Path)`: `.json` -> `JsonMapper`, `.toml` -> `TomlMapper`,
anything else (`.yml`/`.yaml`) -> `YAMLMapper`. Supported extensions: `json`, `yml`, `yaml`, `toml`.
So `config.toml` with `presets/classic.json` and `rules/water.yaml` all coexist and round-trip
through the same DTOs.

Every mapper is configured identically in `ConfigFormats.configure`:

- non-null inclusion on write, so a `null` ("inherit") override field is omitted, not written as null.
- `ACCEPT_SINGLE_VALUE_AS_ARRAY`, so `extends: classic` and `extends: [classic]` both parse.
- `FAIL_ON_UNKNOWN_PROPERTIES` disabled, so a newer config key does not break an older build.

### Jackson stays in adapters/config

`api`/`ports`/`core` are framework-free and carry no Jackson annotations (enforced by ArchUnit in
`:verification`). The annotated DTOs (`ConfigDtos`, `RuleDtos`) live only in `adapters/config` and
mirror the on-disk shape one to one. The neutral domain records (`Preset`, `Rule`, `PlacementRule`,
enums) live in `api.model`. The repositories translate DTO <-> record. When you add a field, add it
as a boxed DTO field plus a neutral record field; never put a `com.fasterxml.jackson.*` annotation in
`api`/`ports`/`core`. See `hexagonal-architecture` for the dependency rule.

## The per-block rule model

A rule is one entry under `rules/`, exactly one per block, modeled by `api.model.Rule`:

```yaml
block: minecraft:oak_leaves   # namespaced BlockId, authoritative (RuleDto.block -> BlockId.of)
state:                        # block-data properties as strings, empty = plain default state
  persistent: true
physics: none                 # none | block_update | fluid_update (PhysicsMode, default none)
placement:                    # present only for a grown decoration; absent = palette modifier only
  minHeight: 1
  maxHeight: 3
  probability: 0.04           # per eligible column, [0,1]
  structure:                  # the 3D footprint placed at generation time, see below
    filler: minecraft:sandstone
    legend:
      S: { block: minecraft:sand, anchor: true }
      W: { block: minecraft:water }
    layers:                   # bottom-to-top; each layer is a list of rows (Z) of symbols (X)
      - ["###", "###", "###"]
      - [" # ", "SW#", " # "]
```

- `state` is a `Map<String,String>` of block-data properties. `RuleResolver.build` turns it into
  `Bukkit.createBlockData("minecraft:oak_leaves[persistent=true]")`. An invalid property throws
  `IllegalArgumentException`, which `RuleResolver` catches and **falls back to the plain default
  state** (it does not fail generation). Resolved data is cached per id; clone before mutating.
- `physics` (`PhysicsMode`): `none` is the stable default (no fluid flow, no leaf decay, no falling
  sand, no redstone recompute on placement). `block_update` schedules a block update, `fluid_update`
  starts a fluid flowing. Applied via `RuleResolver.applyPhysics` after placement. Unknown values
  throw with a clear message listing the three legal names.
- `placement` (`PlacementRule`): present only when the rule grows a decoration column. `structure`
  (`StructurePattern`) declares the 3D footprint placed at the decoration's cell, see below.
  `min/maxHeight` bound the grown column, `probability` is the per-cell roll. A rule with no
  `placement` is a plain palette-block modifier (state/physics only).

A rule with `placement` is a decoration (`Rule.isDecoration()`); `RuleCatalog.decorations()` returns
those in stored order.

## The structure pattern: a 3D footprint per decoration

`structure` (`api.model.StructurePattern` + `LegendEntry`) replaces the old single-block
`requiresGround`/`requiresLiquidNeighbor`/`water` fields with a small, recipe-like 3D pattern: a list
of `layers` stacked bottom-to-top, each layer a list of row-strings, each character a symbol. A
`legend` maps each symbol to a `{ block, state, anchor }` entry; exactly one entry must be
`anchor: true`, marking the cell that is the decoration's own placement position (what
`requiresGround` used to target). Three symbols are reserved and cannot be redefined: `' '` (leave
the cell untouched), `'#'` (ensure solid, filling with `filler` only if the cell is currently air),
and `'.'` (must be air, cleared). All of this is validated at config load in `JacksonRuleRepository`
(rectangular layers, unknown symbols, exactly one anchor), fail-fast with a message naming the file.

This is the load-bearing fix for a real bug: a decoration's ground block placed at generation time
had nothing solid under it, an unsupported "floating gravity block" that fell (as a `FallingBlock`
entity) the first time a real block update reached it, typically the decoration's own later growth.
`structure` lets a rule declare real solid support (and boxed water, and waterlogged blocks via
`state`) as data instead of hardcoding oasis geometry in Java. **Sand cannot be waterlogged** is still
the one hard invariant to remember: a boxed water `legend` entry (walls + floor via `#`/exact blocks,
open top) is how the shipped `sugar_cane` rule waters sand; `state: { waterlogged: "true" }` only
does something on a block that is genuinely waterloggable. See `docs/rules.md` for the full worked
example and `generation-pipeline` for how `adapters/generator.StructurePlacer` realizes a pattern
against Bukkit (`StillWater` still exists separately, for the WFC `Plant` decoration pipeline only).

## Determinism

All placement is seeded from the world seed. `SkyGridDecorationPopulator` derives each cell roll from
`Seeds.blockSeed(worldSeed, x, y, z)` (and mixes in the rule's block id), so the same seed and
coordinates always yield the same decorations regardless of chunk population order. Keep new placement
logic seeded the same way; never use unseeded randomness or wall-clock state.

## How a change flows through the layers

Adding a **new format**: only touch `ConfigFormats` (a new `MapperBuilder` branch and the `SUPPORTED`
list). The DTOs and records are format-agnostic already.

Adding a **preset field**: add a boxed field to `ConfigDtos.PresetDto` (boxed so an omitted key
round-trips as `null` = inherit), add the neutral field to `api.model.Preset`, and map both directions
in `JacksonPresetRepository` (`toPreset` and `toDto`). Inheritance merging happens in the core, not
the adapter (see `world-generator-presets`).

Adding a **rule field**: add a boxed field to `RuleDtos.RuleDto` or `RuleDtos.PlacementDto`, add it to
`api.model.Rule`/`PlacementRule` (validate in the compact constructor), and map both directions in
`JacksonRuleRepository` (`toRule`/`placement` and `toDto`/`placementToDto`). Then consume it where the
block is realized: `RuleResolver` (state/physics) or `SkyGridDecorationPopulator` (placement).

Adding a **new decoration type** (a `Plant`/`LootChest`/`Spawner` sibling) is a preset-tile concern,
not a per-block rule: extend the `ConfigDtos.DecorationDto` sealed union (a new `@JsonSubTypes.Type`
with its `type` discriminator) and the `api.model.Decoration` union. That is generation semantics; see
`skygrid-domain` and `generation-pipeline`.

Regenerate the `schema/` files after any DTO change so editor validation matches the code.

## A rule applies everywhere its block is generated

`RuleCatalog` is shared by every preset: a rule for `minecraft:oak_leaves` applies wherever that block
is placed, in all three algorithms. Concretely a rule reaches the world through:

1. the base grid (the `skygrid`/`noise_skygrid`/`wfc` surface pass, through `RuleResolver.blockData`
   for state and `RuleResolver.physics` for updates), and
2. both populators: `SkyGridDecorationPopulator` (classic algorithms) and the WFC populator each grow
   the decoration rules and apply state/physics.

So a state or physics change is global to that block; a placement (decoration) rule is grown wherever
the grid offers an eligible cell. An empty `RuleCatalog` reproduces plain block placement.

## Where to look

Detailed operator docs and the generated schemas:

- `docs/configuration.md`: the split layout and format matrix for server operators.
- `docs/presets.md`: writing and extending presets (pairs with `world-generator-presets`).
- `docs/rules.md`: the per-block rule reference (state/physics/placement recipes).
- `schema/`: the generated Draft 2020-12 JSON Schemas for editor validation.

Shipped defaults to read for real examples:

- `adapters/config/src/main/resources/config.yml`, `presets/*.yml`, `rules/*.yml`.

Code:

- `adapters/config/.../ConfigFormats.java`, `ConfigDtos.java`, `RuleDtos.java`,
  `JacksonPresetRepository.java`, `JacksonRuleRepository.java`.
- `api/.../model/Rule.java`, `PlacementRule.java`, `PhysicsMode.java`, `StructurePattern.java`,
  `LegendEntry.java`, `RuleCatalog.java`.
- `adapters/generator/.../RuleResolver.java`, `SkyGridDecorationPopulator.java`,
  `StructurePlacer.java`.

# Per-block rules

See also: [Configuration layout](configuration.md), [Presets](presets.md), [overview](README.md).

A rule controls one block. It lives in its own file under `rules/`, and there is exactly one rule per
block, so each block's behavior is tuned on its own. A rule can do three things: pin the block's data
state, decide how the block reacts to updates (physics), and, for a block that grows as a decoration
(like sugar cane), define the conditions under which it is placed.

Rules are shared by every preset: a rule for `minecraft:oak_leaves` applies wherever that block is
generated, in all algorithms. An empty `rules/` directory means "no rules", which reproduces plain
block placement.

## The file

The block id inside the file (`block:`) is authoritative; the file name is only for humans.

```yaml
# rules/oak_leaves.yml
block: minecraft:oak_leaves   # namespaced id, required
state:                        # block-data properties, optional (empty = plain default state)
  persistent: true
physics: none                 # none | block_update | fluid_update (default: none)
placement:                    # optional; present only for a grown decoration
  minHeight: 1
  maxHeight: 3
  probability: 0.04
  structure:                  # the 3D footprint placed at generation time, see below
    filler: minecraft:sandstone
    legend:
      S: { block: minecraft:sand, anchor: true }
      W: { block: minecraft:water }
    layers:                   # bottom-to-top; each layer is a list of rows (Z), each row a string of columns (X)
      - ["###", "###", "###"]
      - [" # ", "SW#", " # "]
```

A rule with a `placement` section is a **decoration** (it grows a column somewhere on the grid). A
rule without one is a plain **palette-block modifier**: it only adjusts the state and physics of a
block that the palette already places.

## `state`: block-data properties

`state` is a map of block-data property names to values, written as strings (`persistent: true`,
`level: 0`, `waterlogged: true`). SkyGrid-ng turns the block id plus this map into a native block
state, effectively `minecraft:oak_leaves[persistent=true]`.

- An empty or absent `state` means "place the block's default state".
- If a property is not valid for the block, the rule quietly falls back to the plain default state
  rather than failing generation.

## `physics`: how the block reacts to updates

`physics` decides whether the placed block is scheduled for a block or fluid update after generation.

| Value          | Effect |
|----------------|--------|
| `none`         | Place the block with no update. Nothing flows, decays, falls, or recomputes. |
| `block_update` | Schedule a block update: support, gravity, and redstone checks run. |
| `fluid_update` | Schedule a fluid update: the fluid starts to flow from this block. |

`none` is the default and the safe, stable choice, and it is why the shipped rules use it:

- **Leaves do not decay.** Vanilla leaves check for a nearby log and decay if none is found. On a
  sparse SkyGrid there usually is no adjacent log, so a block update would make leaves disappear.
- **Fluids do not flow.** A water or lava source left free on the grid would flow down into the void
  and spread, flooding the world and firing a storm of updates. `none` keeps it put.
- **Sand and gravel do not fall.** Gravity-affected blocks stay in place on the grid.

Use `block_update` or `fluid_update` only when you deliberately want a single block to opt back into
vanilla behavior. For a generator that must stay deterministic and lag-free, `none` is almost always
what you want.

**Scope:** `block_update` and `fluid_update` take effect for a **decoration** rule (one with a
`placement`), where a single targeted update is scheduled at the plant. A plain **palette-block**
modifier (a rule without `placement`) is written into the raw chunk during generation, which has no
update to schedule, so its physics is always effectively `none`. This is deliberate: scheduling
updates across every grid block would cause the update storms `none` exists to prevent. Set a
non-`none` mode on a decoration rule, not a palette one.

## `placement`: growing a decoration

Present only when the rule grows a decoration column. Fields:

| Field         | Type      | Meaning |
|---------------|-----------|---------|
| `structure`   | object    | The 3D footprint placed at generation time around the decoration: solid support, boxed water, waterlogged blocks, whatever the pattern declares. See [`structure`](#structure-the-3d-footprint) below. |
| `minHeight`   | integer   | Minimum column height in blocks (at least 1). |
| `maxHeight`   | integer   | Maximum column height (at least `minHeight`). |
| `probability` | number   | Placement chance per eligible column, in `[0, 1]`. |

The generator scans grid cells, rolls `probability` per cell (seeded, see
[Determinism](#determinism)), and on a hit places the `structure` pattern anchored at that cell,
grows the column, and applies the rule's physics.

### `structure`: the 3D footprint

`structure` replaces what used to be `requiresGround`/`requiresLiquidNeighbor`/`water`: instead of a
handful of fixed flags, it is a small, recipe-like pattern that declares exactly what should surround
the decoration, laid out as stacked layers of single-character symbols. This is what makes the fix in
the [sugar-cane worked example](#sugar_cane-grown-on-a-real-solid-footprint) possible: the pattern can
demand a real solid floor under the whole footprint, not just under the one block the plant stands on.

| Field    | Type                        | Meaning |
|----------|-----------------------------|---------|
| `filler` | block id                    | The block used to fill `#` cells, and only when a `#` cell is currently air. Only needed if `#` appears anywhere in `layers`. |
| `legend` | map of symbol to entry      | Maps a single character to `{ block, state, anchor }`. `block` is a namespaced id, `state` is the same string-map convention as the top-level [`state`](#state-block-data-properties) field, and `anchor: true` marks the one cell that is the decoration's own placement position, the same position `requiresGround` used to target. |
| `layers` | list of layers, bottom-to-top | Each layer is itself a list of strings. Layers stack along Y (bottom-to-top); within a layer, each string is a row along Z, and each character in a row is a column along X. |

Three symbols are reserved, always available, and must never be redefined in `legend`:

| Symbol | Meaning |
|--------|---------|
| `' '` (space) | Leave the cell exactly as generation already left it. Untouched, no read, no write. |
| `'#'`  | Ensure solid. If the cell is currently air, fill it with `filler`; otherwise leave whatever is already there alone. |
| `'.'`  | Must be air. The cell is cleared. |

Every other character used anywhere in `layers` must have a matching `legend` entry, or the config
fails to load with a clear error, the same fail-fast style as the old `PhysicsMode`/`WaterMode`
validation. Layers and rows must be rectangular (every row in a layer the same length, every layer the
same number of rows), and exactly one `legend` entry must be marked `anchor: true`, with that entry's
symbol appearing exactly once across the whole pattern; anything else (no anchor, more than one
anchor, more than one cell using the anchor symbol) also fails config load.

## Water: expressed as pattern cells, not a mode

There is no `water` field anymore. The old `none`/`source`/`waterlogged` modes are just specific
`structure` shapes now, declared as data instead of hardcoded in Java:

- What used to be **`water: source`** is now a `legend` entry mapping a symbol to `minecraft:water`,
  boxed in on the floor and the sides by `#` (or other exact solid symbols), with only the top left
  open. A water source with no air to flow into never ticks, so it stays put, fires no update, and
  never floods the void, exactly the old behavior, just declared as a pattern instead of a Java helper.
- What used to be **`water: waterlogged`** is now a `legend` entry with `state: { waterlogged: "true" }`
  on a block that can actually hold water. This still only does something when that block is genuinely
  waterloggable, the same hard Minecraft fact as before: **sand cannot be waterlogged**, which is why
  the shipped sugar-cane rule boxes a water source next to the sand instead of trying to waterlog it.
- What used to be **`water: none`** is simply a pattern with no water symbol in it at all.

## Worked examples from the shipped defaults

### `oak_leaves`: persistent, no decay

Leaves are pinned to `persistent=true` so they are never checked for decay, and `physics: none` so
placement does not trigger that check. They survive on the grid even with no adjacent log. (The
`standard` preset's `cherry_leaves` rule is identical in shape.)

```yaml
# rules/oak_leaves.yml
block: minecraft:oak_leaves
state:
  persistent: true
physics: none
```

### `water`: a stable source that never floods

Generated water is pinned to `level=0` (a full source block) with `physics: none`, so it never flows
out and floods the void around the grid.

```yaml
# rules/water.yml
block: minecraft:water
state:
  level: 0
physics: none
```

### `sugar_cane`: grown on a real solid footprint

Sugar cane is a decoration column, and its `structure` fixes a real bug rather than just illustrating
the format: earlier, sand placed at generation time as the plant's ground had nothing solid under it
either, an unsupported "floating gravity block" that stayed put only until the first genuine block
update (typically the sugar cane's own growth) reached it, at which point it fell as a `FallingBlock`
entity. With many generated oases sharing the same latent defect, that could mean many blocks falling
at once. The `structure` pattern below fixes this at the root, generically, for any decoration:

```yaml
# rules/sugar_cane.yml
block: minecraft:sugar_cane
physics: none
placement:
  minHeight: 1
  maxHeight: 3
  probability: 0.04
  structure:
    filler: minecraft:sandstone        # used for '#' cells only when the cell is currently air
    legend:
      S: { block: minecraft:sand, anchor: true }   # anchor: this cell is the plant's own foot
      W: { block: minecraft:water }
    layers:                            # bottom-to-top; each layer = rows (Z) of symbols (X)
      - ["###", "###", "###"]         # floor, one Y below the ground layer
      - [" # ", "SW#", " # "]         # ground layer: S=anchor/sand, W=boxed water, # = walls
```

The floor layer places `minecraft:sandstone` filler under the entire footprint, so nothing in the
structure can ever be an unsupported gravity block. The ground layer puts the sand at the anchor cell
(the sugar cane's own placement position, the same cell `requiresGround` used to target), boxes a
single water cell in with sandstone on the two remaining sides and the sand as the fourth wall, and
leaves the top open. Because the walls and floor are `minecraft:sandstone`, not sand, the whole basin
is immune to gravity too, unlike the old hardcoded basin that was built entirely out of
`Material.SAND`. The water still never ticks (no air to flow into) and never floods the void, the same
physical effect the old `water: source` mode had, just declared as data instead of hardcoded in Java.

Note this rule has no `state` section: it does not need to pin any block-data property, so the sugar
cane itself uses its default state.

### `golden_dandelion`: a minimal one-cell anchor

Unlike sugar cane, the golden dandelion needs no water and no growth column, just a single
`grass_block` anchor cell for ground support, since `minHeight` and `maxHeight` are both 1.

```yaml
# rules/golden_dandelion.yml
block: minecraft:golden_dandelion
physics: none
placement:
  minHeight: 1
  maxHeight: 1
  probability: 0.03
  structure:
    filler: minecraft:dirt
    legend:
      G: { block: minecraft:grass_block, anchor: true }
    layers:
      - ["G"]
```

`filler` is present for consistency with the other rule files, but it is unused here since the
pattern has no `#` (ENSURE_SOLID) cell.

## Determinism

Everything a rule does is seeded from the world seed. Each cell's decoration roll is derived from the
world seed and the cell coordinates (with the rule's block id mixed in), so the same seed and
coordinates always produce the same result, independent of the order chunks are populated. A config
change therefore only affects newly generated chunks; it never changes how an existing seed maps to a
world.
